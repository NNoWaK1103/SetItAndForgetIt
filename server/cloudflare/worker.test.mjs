import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{MarketDataCache,MarketDataService} from './worker.mjs';
import {readFileSync} from 'node:fs';

const fixture=JSON.parse(readFileSync(new URL('../fixtures/alpaca.json',import.meta.url),'utf8'));
const TOKEN='a'.repeat(64); // Deliberately public test fixture, never a deployment credential.
const authenticated=path=>new Request(path,{headers:{Authorization:`Bearer ${TOKEN}`}});
const START=Date.parse('2026-10-29T14:00:00Z');
class Storage {
  constructor() {this.values=new Map();}
  async get(key) {return structuredClone(this.values.get(key));}
  async put(key,value) {this.values.set(key,structuredClone(value));}
}
function harness(extra={}) {
  let now=START, responder=url=>url.pathname==='/v2/clock'?fixture.clock:url.pathname.endsWith('/snapshots')?fixture.snapshots:fixture.history;
  const requests=[],storage=extra.storage ?? new Storage();
  const env={PROXY_ACCESS_TOKEN:TOKEN,ALPACA_API_KEY_ID:'test-key',ALPACA_API_SECRET_KEY:'test-secret',MARKET_DISPLAY_LICENSE_CONFIRMED:'true',...extra};
  const fetcher=async(url,init)=>{
    requests.push({url,init});
    const result=await responder(url,init);
    return result instanceof Response?result:Response.json(result);
  };
  const service=new MarketDataService(storage,env,{fetcher,now:()=>now});
  const request=async path=>{const response=await service.fetch(authenticated(`https://worker.example${path}`));return {response,body:await response.json()};};
  return {service,storage,env,requests,request,fetcher,now:()=>now,advance:ms=>{now+=ms;},respond:fn=>{responder=fn;}};
}

test('Worker batches Home Depot and watchlist quotes, caches across clients and preserves timestamps',async()=>{
  const h=harness();
  const first=await h.request('/v1/quotes?symbols=HD,BRK.B&indices=I:SPX');
  assert.equal(first.response.status,200);assert.equal(first.body.quotes.find(q=>q.symbol==='HD').price,410.25);
  assert.equal(first.body.quotes.find(q=>q.symbol==='HD').updated,'2026-10-29T13:59:59.123Z');
  assert.equal(first.body.quotes.find(q=>q.symbol==='I:SPX').price,null);
  assert.equal(first.body.refreshAfterSeconds,60,'Unavailable indices must not slow stock polling');
  await Promise.all([h.request('/v1/quotes?symbols=BRK.B,HD'),h.request('/v1/quotes?symbols=HD,BRK.B')]);
  assert.equal(h.requests.length,2,'One batch snapshot and one provider clock');
  assert.ok(h.requests.every(r=>r.init.method==='GET' && r.init.redirect==='manual'));
  assert.ok(h.requests.every(r=>r.init.headers['APCA-API-SECRET-KEY']==='test-secret'));
  const text=JSON.stringify(first.body);assert.ok(!text.includes('test-key') && !text.includes('test-secret'));
  assert.equal(first.response.headers.get('cache-control'),'no-store');
});
test('cache and provider quotas survive a Durable Object instance restart',async()=>{
  const h=harness();await h.request('/v1/quotes?symbols=HD');
  const restarted=new MarketDataService(h.storage,h.env,{fetcher:h.fetcher,now:h.now});
  const result=await restarted.fetch(authenticated('https://worker.example/v1/quotes?symbols=HD'));
  assert.equal(result.status,200);assert.equal(h.requests.length,2);
  assert.equal((await h.storage.get('quota:alpaca')).times.length,2);
});
test('feed selection labels IEX real time and delayed SIP; history cutoff uses SIP bars',async()=>{
  for(const [feed,freshness,label] of [['iex','REALTIME','IEX only'],['sip','REALTIME','SIP'],['delayed_sip','DELAYED','SIP']]) {
    const h=harness({ALPACA_FEED:feed});
    const {body}=await h.request('/v1/quotes?symbols=HD');
    assert.equal(body.quotes[0].freshness,freshness);assert.equal(body.quotes[0].feedLabel,label);
    const {body:history}=await h.request('/v1/history?symbol=HD&range=1D');
    assert.equal(history.points.length,2);assert.equal(history.freshness,freshness);
    const url=h.requests.at(-1).url;
    assert.equal(url.searchParams.get('feed'),feed==='delayed_sip'?'sip':feed);
    if(feed==='delayed_sip') assert.ok(Date.parse(url.searchParams.get('end'))<=START-900_000);
  }
});
test('weekend fallback uses the latest completed daily bar, original timestamp and IEX label',async()=>{
  const h=harness();h.advance(Date.parse('2026-11-08T17:00:00Z')-START);
  h.respond(url=>url.pathname==='/v2/clock'?{timestamp:new Date(h.now()).toISOString(),is_open:false}:{HD:{
    dailyBar:{c:411,h:415,l:405,v:1234,t:'2026-11-06T05:00:00Z'},
    prevDailyBar:{c:400,h:402,l:395,v:999,t:'2026-11-05T05:00:00Z'}
  }});
  const {body}=await h.request('/v1/quotes?symbols=HD');
  assert.deepEqual(body.quotes[0],{symbol:'HD',price:411,previousClose:400,high:415,low:405,volume:1234,
    updated:'2026-11-06T05:00:00.000Z',freshness:'PREVIOUS_CLOSE',feedLabel:'IEX only',delayMinutes:null});
  assert.equal(body.session,'CLOSED');assert.deepEqual(body.staleSymbols,[]);assert.equal(body.warning,null);
  const cached=(await h.request('/v1/quotes?symbols=HD')).body;
  assert.deepEqual(cached.quotes,body.quotes);assert.equal(h.requests.length,2,'Fallback adds no upstream calls and reuses the normal cache');
  const restarted=new MarketDataService(h.storage,h.env,{fetcher:h.fetcher,now:h.now});
  const restored=await (await restarted.fetch(authenticated('https://worker.example/v1/quotes?symbols=HD'))).json();
  assert.deepEqual(restored.quotes,body.quotes);assert.equal(h.requests.length,2);
});
test('previous-daily-only fallback has no invented comparison close and is never real-time or delayed',async()=>{
  for(const [feed,label] of [['iex','IEX only'],['sip','SIP'],['delayed_sip','SIP']]) {
    const h=harness({ALPACA_FEED:feed});
    h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:{prevDailyBar:{c:400,h:402,l:395,v:999,t:'2026-10-28T04:00:00Z'}}});
    const q=(await h.request('/v1/quotes?symbols=HD')).body.quotes[0];
    assert.equal(q.price,400);assert.equal(q.previousClose,null);assert.equal(q.freshness,'PREVIOUS_CLOSE');
    assert.equal(q.feedLabel,label);assert.equal(q.delayMinutes,null);assert.equal(q.updated,'2026-10-28T04:00:00.000Z');
    assert.equal(q.high,402);assert.equal(q.low,395);assert.equal(q.volume,999);
  }
});
test('daily fallback selects the newest valid completed bar and never compares a close to itself',async()=>{
  for(const item of [
    {dailyBar:{c:390,t:'2026-10-27T04:00:00Z'},prevDailyBar:{c:400,t:'2026-10-28T04:00:00Z'}},
    {dailyBar:{c:400,t:'2026-10-28T04:00:00Z'},prevDailyBar:{c:400,t:'2026-10-28T04:00:00Z'}},
    {dailyBar:{c:405,t:'2026-10-29T04:00:00Z'},prevDailyBar:{c:400,t:'2026-10-28T04:00:00Z'}}
  ]) {
    const h=harness();h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:item});
    const q=(await h.request('/v1/quotes?symbols=HD')).body.quotes[0];
    assert.equal(q.price,400);assert.equal(q.previousClose,null);assert.equal(q.updated,'2026-10-28T04:00:00.000Z');
  }
});
test('absent, invalid, untimestamped, future and current-day bars never fabricate a fallback',async()=>{
  for(const bar of [undefined,{}, {c:400}, {c:0,t:'2026-10-28T04:00:00Z'}, {c:-1,t:'2026-10-28T04:00:00Z'},
    {c:'400',t:'2026-10-28T04:00:00Z'}, {c:400,t:'bad'}, {c:400,t:'2026-10-28'},
    {c:400,t:'2026-10-30T04:00:00Z'}, {c:400,t:'2026-10-29T04:00:00Z'}]) {
    const h=harness();h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:{dailyBar:bar,prevDailyBar:bar}});
    const q=(await h.request('/v1/quotes?symbols=HD')).body.quotes[0];
    assert.deepEqual(q,{symbol:'HD',price:null,freshness:'UNAVAILABLE',updated:null});
  }
});
test('completed-day fallback uses Eastern calendar dates around midnight and DST',async()=>{
  const h=harness();h.advance(Date.parse('2026-11-02T04:30:00Z')-START); // Still November 1 in New York.
  h.respond(url=>url.pathname==='/v2/clock'?{timestamp:new Date(h.now()).toISOString(),is_open:false}:{HD:{
    dailyBar:{c:410,t:'2026-11-01T04:00:00Z'},prevDailyBar:{c:400,t:'2026-10-30T04:00:00Z'}
  }});
  const q=(await h.request('/v1/quotes?symbols=HD')).body.quotes[0];
  assert.equal(q.price,400);assert.equal(q.updated,'2026-10-30T04:00:00.000Z');
});
test('fallback stays dated and marked stale across a failed refresh, then recovers to a real quote',async()=>{
  const h=harness();h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:{prevDailyBar:{c:400,t:'2026-10-28T04:00:00Z'}}});
  const first=(await h.request('/v1/quotes?symbols=HD')).body.quotes[0];
  h.advance(61_000);h.respond(()=>new Response('{}',{status:401}));
  const failed=(await h.request('/v1/quotes?symbols=HD')).body;
  assert.deepEqual(failed.quotes[0],first);assert.deepEqual(failed.staleSymbols,['HD']);assert.match(failed.warning,/unavailable/);
  assert.equal(failed.quotes[0].freshness,'PREVIOUS_CLOSE');
  h.advance(61_000);h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:fixture.snapshots);
  const recovered=(await h.request('/v1/quotes?symbols=HD')).body;
  assert.equal(recovered.quotes[0].price,410.25);assert.equal(recovered.quotes[0].freshness,'REALTIME');assert.deepEqual(recovered.staleSymbols,[]);
});
test('an older daily fallback cannot replace a newer cached quote',async()=>{
  const h=harness();const first=(await h.request('/v1/quotes?symbols=HD')).body.quotes[0];
  h.advance(61_000);h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:{dailyBar:{c:400,t:'2026-10-28T04:00:00Z'}}});
  const result=(await h.request('/v1/quotes?symbols=HD')).body;
  assert.deepEqual(result.quotes[0],first);assert.deepEqual(result.staleSymbols,['HD']);
});
test('mixed quotes keep live, completed-day and missing data separate and sanitize fallback metrics',async()=>{
  const h=harness();h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:fixture.snapshots.HD,AAPL:{
    prevDailyBar:{c:200,t:'2026-10-28T04:00:00Z',h:'205',l:-1,v:'999'}
  }});
  const {body}=await h.request('/v1/quotes?symbols=HD,AAPL,MSFT');
  const quotes=Object.fromEntries(body.quotes.map(q=>[q.symbol,q]));
  assert.equal(quotes.HD.freshness,'REALTIME');assert.equal(quotes.HD.price,410.25);
  assert.equal(quotes.AAPL.freshness,'PREVIOUS_CLOSE');assert.equal(quotes.AAPL.price,200);
  assert.equal(quotes.AAPL.high,null);assert.equal(quotes.AAPL.low,null);assert.equal(quotes.AAPL.volume,null);
  assert.equal(quotes.MSFT.freshness,'UNAVAILABLE');assert.equal(quotes.MSFT.price,null);
  assert.deepEqual(body.staleSymbols,[]);assert.equal(h.requests.length,2);
});
test('a newer completed bar replaces an older cached fallback without becoming stale or live',async()=>{
  const h=harness();h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:{prevDailyBar:{c:390,t:'2026-10-27T04:00:00Z'}}});
  await h.request('/v1/quotes?symbols=HD');h.advance(61_000);
  h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:{dailyBar:{c:400,t:'2026-10-28T04:00:00Z'},prevDailyBar:{c:390,t:'2026-10-27T04:00:00Z'}}});
  const {body}=await h.request('/v1/quotes?symbols=HD');
  assert.equal(body.quotes[0].price,400);assert.equal(body.quotes[0].previousClose,390);
  assert.equal(body.quotes[0].updated,'2026-10-28T04:00:00.000Z');assert.equal(body.quotes[0].freshness,'PREVIOUS_CLOSE');
  assert.deepEqual(body.staleSymbols,[]);
});
test('fallback does not hide provider rejection or fill missing intraday history',async()=>{
  const h=harness();h.respond(()=>new Response('{}',{status:403}));
  const failed=(await h.request('/v1/quotes?symbols=HD')).body;
  assert.equal(failed.quotes[0].price,null);assert.equal(failed.quotes[0].freshness,'UNAVAILABLE');assert.match(failed.warning,/unavailable/);
  const daily=harness();daily.respond(url=>url.pathname==='/v2/clock'?fixture.clock:url.pathname.endsWith('/snapshots')?{HD:{prevDailyBar:{c:400,t:'2026-10-28T04:00:00Z'}}}:{bars:{HD:[]},next_page_token:null});
  assert.equal((await daily.request('/v1/quotes?symbols=HD')).body.quotes[0].price,400);
  assert.deepEqual((await daily.request('/v1/history?symbol=HD&range=1D')).body.points,[]);
});
test('429 retains valid quotes, respects Retry-After and blocks other upstream calls globally',async()=>{
  const h=harness();const original=(await h.request('/v1/quotes?symbols=HD')).body.quotes[0];
  h.advance(61_000);h.respond(()=>new Response('{}',{status:429,headers:{'Retry-After':'240'}}));
  const {body}=await h.request('/v1/quotes?symbols=HD');
  assert.deepEqual(body.quotes[0],original);assert.deepEqual(body.staleSymbols,['HD']);assert.match(body.warning,/unavailable/);
  assert.ok(body.refreshAfterSeconds>=240);assert.equal(h.requests.length,3);
  await h.request('/v1/history?symbol=HD&range=1Y');assert.equal(h.requests.length,3);
  h.advance(239_000);await h.request('/v1/quotes?symbols=HD');assert.equal(h.requests.length,3);
});
test('quota exhaustion does not call upstream, fabricate quotes or leak exception details',async()=>{
  const h=harness({ALPACA_REQUESTS_PER_MINUTE:'1'});
  const {body}=await h.request('/v1/quotes?symbols=HD');assert.equal(body.session,'UNKNOWN');assert.equal(h.requests.length,1);
  const result=await h.request('/v1/history?symbol=HD&range=1D');assert.deepEqual(result.body.points,[]);assert.equal(h.requests.length,1);
  assert.ok(!JSON.stringify(result.body).includes('test-secret'));
});
test('partial snapshot retains the original valid quote without relabeling its timestamp',async()=>{
  const h=harness();const first=(await h.request('/v1/quotes?symbols=HD,BRK.B')).body;
  h.advance(61_000);h.respond(url=>url.pathname==='/v2/clock'?fixture.clock:{HD:fixture.snapshots.HD});
  const {body}=await h.request('/v1/quotes?symbols=HD,BRK.B');
  assert.deepEqual(body.quotes.find(q=>q.symbol==='BRK.B'),first.quotes.find(q=>q.symbol==='BRK.B'));
  assert.deepEqual(body.staleSymbols,['BRK.B']);
});
test('history supports every range, caches, completes pagination and refuses partial pages',async()=>{
  const h=harness();
  for(const range of ['1D','1W','1M','3M','1Y']) {
    assert.equal((await h.request(`/v1/history?symbol=HD&range=${range}`)).body.points.length,2);
    await h.request(`/v1/history?symbol=HD&range=${range}`);
  }
  assert.equal(h.requests.length,5);
  h.advance(301_000);h.respond(url=>url.searchParams.has('page_token')?{bars:{HD:[{t:'2026-10-29T13:40:00Z',c:410}]},next_page_token:null}:{...fixture.history,next_page_token:'next'});
  const chart=(await h.request('/v1/history?symbol=HD&range=1D')).body;
  assert.equal(chart.points.length,3);assert.equal(chart.pending,false);
  h.advance(301_000);h.respond(()=>({...fixture.history,next_page_token:'loop'}));
  const failed=(await h.request('/v1/history?symbol=HD&range=1D')).body;
  assert.deepEqual(failed.points,chart.points);assert.equal(failed.stale,true);assert.match(failed.message,/stale/);
});
test('history failures and a new Eastern day do not show yesterday as intraday today',async()=>{
  const h=harness();await h.request('/v1/history?symbol=HD&range=1D');
  h.advance(86_400_000);h.respond(()=>{throw new Error('test-secret');});
  const {body}=await h.request('/v1/history?symbol=HD&range=1D');assert.deepEqual(body.points,[]);
  assert.ok(!JSON.stringify(body).includes('test-secret'));
});
test('cash index endpoint returns actual entitled EOD values and shares them with quotes',async()=>{
  const h=harness({MASSIVE_API_KEY:'index-secret'});
  h.respond(url=>url.host==='api.massive.com'?{results:[{t:START-172800_000,c:100},{t:START-86400_000,c:105,h:106,l:101}]}:url.pathname==='/v2/clock'?fixture.clock:fixture.snapshots);
  const {body}=await h.request('/v1/indices');assert.equal(body.quotes.length,3);
  assert.equal(body.quotes[0].price,105);assert.equal(body.quotes[0].previousClose,100);assert.equal(body.quotes[0].freshness,'PREVIOUS_CLOSE');
  assert.ok(h.requests.every(r=>r.init.headers.Authorization==='Bearer index-secret'));
  await h.request('/v1/quotes?symbols=HD&indices=I:SPX,I:DJI,I:COMP');assert.equal(h.requests.length,5);
  assert.ok(!JSON.stringify(body).includes('index-secret'));
});
test('index failures retain values and cannot label them real time',async()=>{
  const h=harness({MASSIVE_API_KEY:'index-secret'});h.respond(()=>({results:[{t:START-86400_000,c:100}]}));
  await h.request('/v1/indices?symbols=I:SPX');h.advance(901_000);h.respond(()=>new Response('{}',{status:403}));
  const {body}=await h.request('/v1/indices?symbols=I:SPX');assert.equal(body.quotes[0].price,100);
  assert.equal(body.quotes[0].freshness,'PREVIOUS_CLOSE');assert.deepEqual(body.staleSymbols,['I:SPX']);
});
test('unconfigured secrets, unknown routes and invalid inputs fail safely without provider calls',async()=>{
  const h=harness({ALPACA_API_KEY_ID:''});
  assert.equal((await h.request('/health')).body.configured,false);
  assert.equal((await h.request('/v1/quotes?symbols=HD')).body.error,'Stock data not configured');
  assert.equal((await h.request('/nope')).response.status,404);assert.equal(h.requests.length,0);
  const configured=harness();
  for(const path of ['/v1/quotes?symbols=EVIL','/v1/quotes?symbols=HD&indices=BAD','/v1/history?symbol=HD&range=2Y','/v1/history?symbol=HD,AAPL&range=1D','/v1/quotes?symbols=HD%26apikey=bad']) assert.equal((await configured.request(path)).response.status,400);
  assert.equal(configured.requests.length,0);
  const post=await configured.service.fetch(new Request('https://worker.example/v1/quotes',{method:'POST',headers:{Authorization:`Bearer ${TOKEN}`}}));assert.equal(post.status,405);
});
test('operator allowlist supports a large company universe without asserting Fortune membership',async()=>{
  const symbols=Array.from({length:500},(_,i)=>`S${i}`);
  const h=harness({MARKET_ALLOWED_SYMBOLS:['HD',...symbols].join(',')});
  assert.equal((await h.request(`/v1/quotes?symbols=${symbols.slice(0,50).join(',')}`)).response.status,200);
  assert.equal((await h.request(`/v1/quotes?symbols=${symbols.slice(0,53).join(',')}`)).response.status,400);
  assert.equal((await h.request('/v1/quotes?symbols=AAPL')).response.status,400);
});
test('Worker entry delegates to a single SQLite Durable Object and returns a safe binding failure',async()=>{
  const storage=new Storage();assert.ok(new MarketDataCache({storage},{}).service);
  let name;
  const env={PROXY_ACCESS_TOKEN:TOKEN,MARKET_CACHE:{idFromName:value=>{name=value;return 'id';},get:()=>({fetch:()=>Response.json({ok:true})})}};
  assert.deepEqual(await (await worker.fetch(authenticated('https://example.com/health'),env)).json(),{ok:true});
  assert.equal(name,'openticker-market-v1');
  assert.equal((await worker.fetch(new Request('https://example.com/health'),{})).status,503);
});

test('every endpoint, including health and unknown routes, rejects unauthenticated traffic before storage',async()=>{
  let touches=0;
  const env={PROXY_ACCESS_TOKEN:TOKEN,MARKET_CACHE:{idFromName(){touches++;throw Error('must not touch');}}};
  for(const path of ['/health','/v1/quotes?symbols=HD','/v1/history?symbol=HD&range=1D','/v1/indices','/unknown']) {
    for(const authorization of [null,'','Bearer wrong',`Bearer ${'b'.repeat(64)}`,`Basic ${TOKEN}`,`Bearer ${TOKEN}, Bearer ${TOKEN}`,`Bearer ${TOKEN.toUpperCase()}`]) {
      const request=new Request(`https://worker.example${path}`,{headers:authorization===null?{}:{Authorization:authorization}});
      const response=await worker.fetch(request,env);
      assert.equal(response.status,401);assert.equal(response.headers.get('cache-control'),'no-store');
      assert.equal(response.headers.get('access-control-allow-origin'),null);
      assert.equal(await response.text(),'{"error":"Stock connection access denied"}');
    }
  }
  assert.equal(touches,0);
});
test('absent or malformed server access token fails closed even with upstream credentials',async()=>{
  for(const token of [undefined,'','short','a'.repeat(63),'a'.repeat(65),'A'.repeat(64)]) {
    const h=harness({PROXY_ACCESS_TOKEN:token});
    assert.equal((await h.request('/health')).response.status,503);
    assert.equal((await h.request('/v1/quotes?symbols=HD')).response.status,503);
    assert.equal(h.requests.length,0);assert.equal(h.storage.values.size,0);
  }
});
test('direct Durable Object requests cannot bypass authentication; rotation invalidates the old token',async()=>{
  const h=harness();
  assert.equal((await h.service.fetch(new Request('https://worker.example/health'))).status,401);
  assert.equal(h.storage.values.size,0);
  h.env.PROXY_ACCESS_TOKEN='b'.repeat(64);
  assert.equal((await h.request('/health')).response.status,401);
  const rotated=new Request('https://worker.example/health',{headers:{Authorization:`Bearer ${'b'.repeat(64)}`}});
  assert.equal((await h.service.fetch(rotated)).status,200);
});
test('access token never reaches upstream, response, or persistent data; upstream host and headers are fixed',async()=>{
  const h=harness();
  const {body}=await h.request('/v1/quotes?symbols=HD');
  assert.deepEqual(h.requests.map(r=>r.url.origin),['https://data.alpaca.markets','https://api.alpaca.markets']);
  assert.ok(h.requests.every(r=>r.init.headers['APCA-API-KEY-ID']==='test-key' && r.init.headers.Authorization===undefined));
  assert.ok(!JSON.stringify(body).includes(TOKEN));
  assert.ok(!JSON.stringify([...h.storage.values]).includes(TOKEN));
  assert.ok(h.requests.every(r=>!JSON.stringify(r.init).includes(TOKEN)));
});
test('query credentials, redirect targets, duplicate parameters and unsafe request forms are rejected',async()=>{
  const h=harness();
  for(const path of ['/health?token=oops','/v1/quotes?symbols=HD&symbols=AAPL','/v1/quotes?symbols=HD&url=https://evil.example','/v1/history?symbol=HD&range=1D&feed=sip'])
    assert.equal((await h.request(path)).response.status,400);
  assert.equal((await h.service.fetch(authenticated('http://worker.example/health'))).status,400);
  assert.equal((await h.request('/v1/quotes?symbols='+ 'A'.repeat(2100))).response.status,414);
  assert.equal((await h.service.fetch(new Request('https://worker.example/health',{method:'OPTIONS',headers:{Authorization:`Bearer ${TOKEN}`}}))).status,405);
  assert.equal(h.requests.length,0);assert.equal(h.storage.values.size,0);
});
test('private request budget is shared, persists across restarts, and expires at the window boundary',async()=>{
  const h=harness({PROXY_REQUESTS_PER_MINUTE:'2',PROXY_REQUESTS_PER_DAY:'3'});
  assert.equal((await h.request('/health')).response.status,200);
  assert.equal((await h.request('/health')).response.status,200);
  const restarted=new MarketDataService(h.storage,h.env,{fetcher:h.fetcher,now:h.now});
  let response=await restarted.fetch(authenticated('https://worker.example/health'));
  assert.equal(response.status,429);assert.ok(Number(response.headers.get('Retry-After'))>=60);
  h.advance(60_001);assert.equal((await h.request('/health')).response.status,200);
  h.advance(60_001);response=(await h.request('/health')).response;
  assert.equal(response.status,429);assert.ok(Number(response.headers.get('Retry-After'))>60);
  h.advance(86400_000);assert.equal((await h.request('/health')).response.status,200);
});
test('invalid budgets or failed storage do not fail open',async()=>{
  for(const extra of [{PROXY_REQUESTS_PER_MINUTE:'0'},{PROXY_REQUESTS_PER_DAY:'not-a-number'},{storage:{get:async()=>{throw Error('secret');}}}]) {
    const h=harness(extra);const {response,body}=await h.request('/health');
    assert.equal(response.status,503);assert.ok(!JSON.stringify(body).includes('secret'));assert.equal(h.requests.length,0);
  }
});
