import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{MarketDataCache,MarketDataService} from './worker.mjs';
import {readFileSync} from 'node:fs';

const fixture=JSON.parse(readFileSync(new URL('../fixtures/alpaca.json',import.meta.url),'utf8'));
const START=Date.parse('2026-10-29T14:00:00Z');
class Storage {
  constructor() {this.values=new Map();}
  async get(key) {return structuredClone(this.values.get(key));}
  async put(key,value) {this.values.set(key,structuredClone(value));}
}
function harness(extra={}) {
  let now=START, responder=url=>url.pathname==='/v2/clock'?fixture.clock:url.pathname.endsWith('/snapshots')?fixture.snapshots:fixture.history;
  const requests=[],storage=extra.storage ?? new Storage();
  const env={ALPACA_API_KEY_ID:'test-key',ALPACA_API_SECRET_KEY:'test-secret',MARKET_DISPLAY_LICENSE_CONFIRMED:'true',...extra};
  const fetcher=async(url,init)=>{
    requests.push({url,init});
    const result=await responder(url,init);
    return result instanceof Response?result:Response.json(result);
  };
  const service=new MarketDataService(storage,env,{fetcher,now:()=>now});
  const request=async path=>{const response=await service.fetch(new Request(`https://worker.example${path}`));return {response,body:await response.json()};};
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
  assert.ok(h.requests.every(r=>r.init.method==='GET' && r.init.redirect==='error'));
  assert.ok(h.requests.every(r=>r.init.headers['APCA-API-SECRET-KEY']==='test-secret'));
  const text=JSON.stringify(first.body);assert.ok(!text.includes('test-key') && !text.includes('test-secret'));
  assert.equal(first.response.headers.get('cache-control'),'no-store');
});
test('cache and provider quotas survive a Durable Object instance restart',async()=>{
  const h=harness();await h.request('/v1/quotes?symbols=HD');
  const restarted=new MarketDataService(h.storage,h.env,{fetcher:h.fetcher,now:h.now});
  const result=await restarted.fetch(new Request('https://worker.example/v1/quotes?symbols=HD'));
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
  const post=await configured.service.fetch(new Request('https://worker.example/v1/quotes',{method:'POST'}));assert.equal(post.status,405);
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
  const env={MARKET_CACHE:{idFromName:value=>{name=value;return 'id';},get:()=>({fetch:()=>Response.json({ok:true})})}};
  assert.deepEqual(await (await worker.fetch(new Request('https://example.com/health'),env)).json(),{ok:true});
  assert.equal(name,'openticker-market-v1');
  assert.equal((await worker.fetch(new Request('https://example.com/health'),{})).status,503);
});
