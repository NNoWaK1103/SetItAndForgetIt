// User-operated, read-only verifier. No credentials in arguments, files or output.
import readline from 'node:readline/promises';
import {stdin,stdout} from 'node:process';

class VerificationError extends Error {}

function readToken() {
  if(!stdin.isTTY) throw new VerificationError('Run in an interactive terminal; do not pipe credentials.');
  stdout.write('Private access token (input hidden): ');
  stdin.setRawMode(true);stdin.resume();
  return new Promise((resolve,reject)=>{
    let value='';
    const finish=(error)=>{
      stdin.off('data',data);stdin.setRawMode(false);stdin.pause();stdout.write('\n');
      error?reject(error):resolve(value);
    };
    const data=chunk=>{
      for(const ch of chunk.toString()) {
        if(ch==='\u0003') {finish(new VerificationError('Cancelled'));return;}
        if(ch==='\r' || ch==='\n') {finish();return;}
        if(ch==='\u007f' || ch==='\b') value=value.slice(0,-1);
        else if(/[a-f0-9]/.test(ch) && value.length<65) value+=ch;
      }
    };
    stdin.on('data',data);
  });
}

try {
  const prompt=readline.createInterface({input:stdin,output:stdout});
  let base;
  try {base=new URL((await prompt.question('Worker base HTTPS URL: ')).trim());} finally {prompt.close();}
  if(base.protocol!=='https:' || base.username || base.password || base.search || base.hash || base.pathname!=='/' || base.port)
    throw new VerificationError('Use the exact HTTPS Worker origin, with no path, credentials, port, query or fragment.');
  let token=await readToken();
  if(!/^[a-f0-9]{64}$/.test(token)) throw new VerificationError('Expected exactly 64 lowercase hexadecimal characters.');
  const request=(path,credential)=>fetch(new URL(path,base),{method:'GET',redirect:'error',signal:AbortSignal.timeout(25000),headers:credential?{Authorization:`Bearer ${credential}`}:{}});
  for(const path of ['/health','/v1/quotes?symbols=HD','/v1/history?symbol=HD&range=1D','/v1/indices']) {
    const response=await request(path);
    if(response.status!==401) throw new VerificationError(`Access-control check failed: unauthenticated ${path.split('?')[0]} returned ${response.status}; expected 401. Stop before adding provider keys.`);
    await response.body?.cancel();
  }
  const wrong=(token[0]==='a'?'b':'a')+token.slice(1);
  let response=await request('/health',wrong);
  if(response.status!==401) throw new VerificationError('Incorrect-token check failed. Stop before adding provider keys.');
  await response.body?.cancel();
  response=await request('/health',token);
  if(response.status!==200) throw new VerificationError(`Authenticated health returned ${response.status}. Check setup; do not disable access controls.`);
  const health=await response.json();
  console.log('PASS: all endpoints reject missing tokens; incorrect token rejected; private health works.');
  if(!health.configured) console.log('Provider not configured yet. Access control is ready for your manual provider-secret setup.');
  else {
    for(const path of ['/v1/quotes?symbols=HD','/v1/history?symbol=HD&range=1D','/v1/indices']) {
      response=await request(path,token);
      if(response.status!==200) throw new VerificationError(`Authenticated ${path.split('?')[0]} returned ${response.status}.`);
      const body=await response.json();
      if(path.includes('/quotes')) console.log(body.quotes?.some(q=>q.price>0)?'PASS: a provider quote is present.':'Connection works, but no valid provider quote yet. Check entitlement/key/feed settings.');
      else if(path.includes('/history')) console.log(body.points?.length>=2?'PASS: history points are present.':'History is currently unavailable. This can be normal outside the current trading session.');
      else console.log('PASS: index endpoint responds (optional provider may be unconfigured).');
    }
  }
  token='';
} catch(error) {
  // Avoid dumping request options, headers, response bodies or stack traces.
  console.error(error instanceof VerificationError?error.message:'Verification could not finish. Check the connection and retry; no credentials were printed.');
  process.exitCode=1;
}
