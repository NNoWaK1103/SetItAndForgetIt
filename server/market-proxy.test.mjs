import test from 'node:test';
import assert from 'node:assert/strict';
import { MarketService, endOfDayQuote, snapshotQuote, mapBars, sessionFrom, easternDate, createServer } from './market-proxy.mjs';

const now=Date.parse('2026-10-29T14:00:00Z');
const bars={results:[{t:now-86400000,c:400,h:405,l:390,v:100},{t:now,c:410,h:415,l:399,v:200}]};
test('EOD mapping uses actual closing bars and prior session comparison',()=>{
  const q=endOfDayQuote('HD',bars);
  assert.equal(q.price,410);assert.equal(q.previousClose,400);assert.equal(q.freshness,'PREVIOUS_CLOSE');
  assert.equal(q.updated,new Date(now).toISOString());assert.equal(q.volume,200);
  assert.equal(endOfDayQuote('HD',{resultsCount:0}).price,null);
  assert.throws(()=>mapBars({error:'quota'}));
});
test('snapshot quotes retain delayed entitlement and data timestamp',()=>{
  const q=snapshotQuote({ticker:'HD',min:{c:410,t:now},day:{h:415,l:399,v:200},prevDay:{c:400}},'DELAYED');
  assert.equal(q.price,410);assert.equal(q.delayMinutes,15);assert.equal(q.freshness,'DELAYED');
  assert.equal(snapshotQuote({ticker:'HD'},'REALTIME').freshness,'UNAVAILABLE');
});
test('provider session handles holidays and early closes without clock inference',()=>{
  assert.equal(sessionFrom({exchanges:{nyse:'closed'},market:'open'}),'CLOSED');
  assert.equal(sessionFrom({exchanges:{nyse:'open'}}),'OPEN');assert.equal(sessionFrom({}),'UNKNOWN');
  assert.equal(sessionFrom({exchanges:{nyse:'extended-hours'}}),'CLOSED');
  assert.equal(easternDate(new Date('2026-07-04T02:00:00Z')),'2026-07-03');
  assert.equal(easternDate(new Date('2026-12-01T04:30:00Z')),'2026-11-30');
});
function harness() {
  let time=now;const pending=[];const requests=[];let failing=false;
  const service=new MarketService({apiKey:'test-only',now:()=>time,requestsPerMinute:5,
    schedule:(fn,delay)=>pending.push({fn,at:time+delay}),fetcher:async(url,options)=>{
      requests.push({url,options,time});
      if(failing) return {ok:false,status:429};
      return {ok:true,json:async()=>url.pathname.includes('marketstatus')?{exchanges:{nyse:'open'},serverTime:new Date(time).toISOString()}:bars};
    }});
  return {service,requests,pending,fail:()=>{failing=true;},advance:value=>{time=value;},
    run:async()=>{const job=pending.shift();assert.ok(job);time=Math.max(time,job.at);await job.fn();},time:()=>time};
}
test('shared queue enforces provider quota across endpoints and coalesces duplicate requests',async()=>{
  const h=harness();h.service.quotes(['HD','AAPL'],[]);h.service.history('HD','1Y');
  h.service.quotes(['HD','AAPL'],[]);assert.equal(h.service.jobs.size,4);
  for(let i=0;i<4;i++) await h.run();
  assert.equal(h.requests.length,4);
  assert.ok(h.requests.slice(1).every((r,i)=>r.time-h.requests[i].time>=12000));
  assert.equal(h.requests[0].url.searchParams.has('apiKey'),false);
  assert.equal(h.requests[0].options.headers.Authorization,'Bearer test-only');
  const response=h.service.quotes(['HD'],[]);
  assert.equal(response.quotes[0].price,410);assert.equal(response.session,'OPEN');
  assert.equal(h.service.history('HD','1Y').points.length,2);
});
test('failures retain valid data and respect backoff',async()=>{
  const h=harness();h.service.quotes(['HD'],[]);await h.run();await h.run();
  const valid=h.service.quotes(['HD'],[]).quotes[0];h.fail();h.advance(now+1000000);
  h.service.quotes(['HD'],[]);await h.run();await h.run();
  const stale=h.service.quotes(['HD'],[]);
  assert.deepEqual(stale.quotes[0],valid);assert.match(stale.warning,/failed/);
  assert.deepEqual(stale.staleSymbols,['HD']);
  assert.equal(h.service.jobs.size,0);
  assert.ok(h.service.cache.get('quote:HD').due>h.time());
});
test('indices stay neutral without a separate entitlement and inputs are bounded',()=>{
  const h=harness();const q=h.service.quotes(['HD'],['I:SPX']).quotes.find(q=>q.symbol==='I:SPX');
  assert.equal(q.price,null);assert.equal(q.freshness,'UNAVAILABLE');
  assert.throws(()=>h.service.quotes(['HD?apiKey=secret'],[]));assert.throws(()=>h.service.quotes(['HD'],['BAD']));
  assert.throws(()=>h.service.history('HD','2Y'));
  assert.throws(()=>h.service.validate(Array(53).fill('HD')));
  assert.throws(()=>new MarketService({apiKey:'test-only',requestsPerMinute:0}));
});
test('HTTP API serves normalized envelopes and rejects unknown routes',async()=>{
  const h=harness(), server=createServer(h.service);
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  try {
    const response=await fetch(`${base}/v1/quotes?symbols=HD`);assert.equal(response.status,200);
    assert.equal((await response.json()).quotes[0].symbol,'HD');
    assert.equal((await fetch(`${base}/unknown`)).status,404);
    assert.equal((await fetch(`${base}/v1/history?symbol=HD&range=bad`)).status,400);
  } finally { await new Promise(resolve=>server.close(resolve)); }
});
