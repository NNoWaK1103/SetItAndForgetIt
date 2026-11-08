// Offline Cloudflare/workerd integration test. All credentials and prices are fake fixtures.
import test from 'node:test';
import assert from 'node:assert/strict';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
const TOKEN='a'.repeat(64);
const fixture=JSON.parse(readFileSync(new URL('../fixtures/alpaca.json',import.meta.url),'utf8'));

test('workerd + SQLite Durable Object: fails closed, accepts private auth, caches and never follows upstream redirects',async()=>{
  const seen=[];
  const outboundService=async request=>{
    const url=new URL(request.url);seen.push(url);
    assert.equal(request.headers.get('APCA-API-KEY-ID'),'fixture-key');
    assert.equal(request.headers.get('APCA-API-SECRET-KEY'),'fixture-secret');
    assert.equal(request.headers.get('Authorization'),null);
    if(url.origin==='https://data.alpaca.markets' && url.pathname==='/v2/stocks/snapshots') return Response.json(fixture.snapshots);
    if(url.origin==='https://api.alpaca.markets' && url.pathname==='/v2/clock') return Response.json(fixture.clock);
    if(url.origin==='https://data.alpaca.markets' && url.pathname==='/v2/stocks/bars') return new Response('',{status:302,headers:{Location:'https://evil.example/collect'}});
    throw new Error('Unexpected network request: '+url.origin);
  };
  const runtime=new Miniflare(convertV4MiniflareOptions({modules:true,scriptPath:fileURLToPath(new URL('./worker.mjs',import.meta.url)),
    compatibilityDate:'2026-10-30',durableObjects:{MARKET_CACHE:{className:'MarketDataCache',useSQLite:true}},
    bindings:{PROXY_ACCESS_TOKEN:TOKEN,ALPACA_API_KEY_ID:'fixture-key',ALPACA_API_SECRET_KEY:'fixture-secret',MARKET_DISPLAY_LICENSE_CONFIRMED:'true'},outboundService}));
  const auth={headers:{Authorization:`Bearer ${TOKEN}`}};
  try {
    assert.equal((await runtime.dispatchFetch('https://worker.example/health')).status,401);
    assert.equal((await runtime.dispatchFetch('https://worker.example/health',auth)).status,200);
    let response=await runtime.dispatchFetch('https://worker.example/v1/quotes?symbols=HD',auth);
    assert.equal(response.status,200);let body=await response.json();assert.equal(body.quotes[0].price,410.25);
    response=await runtime.dispatchFetch('https://worker.example/v1/quotes?symbols=HD',auth);
    assert.equal((await response.json()).quotes[0].price,410.25,'Second request must use SQLite cache: no second upstream mock exists');
    response=await runtime.dispatchFetch('https://worker.example/v1/history?symbol=HD&range=1D',auth);
    body=await response.json();assert.deepEqual(body.points,[]);assert.equal(body.stale,true);
    assert.deepEqual(seen.map(url=>url.pathname),['/v2/stocks/snapshots','/v2/clock','/v2/stocks/bars']);
  } finally {await runtime.dispose();}
});

test('workerd + SQLite Durable Object: completed-day fallback stays private, dated and cached',async()=>{
  const prior=new Date(Date.now()-3*86400_000).toISOString(),before=new Date(Date.parse(prior)-86400_000).toISOString();
  const seen=[];
  const outboundService=async request=>{
    const url=new URL(request.url);seen.push(url.pathname);
    assert.equal(request.headers.get('Authorization'),null);
    if(url.pathname==='/v2/clock') return Response.json({timestamp:new Date().toISOString(),is_open:false});
    assert.equal(url.origin,'https://data.alpaca.markets');assert.equal(url.pathname,'/v2/stocks/snapshots');
    return Response.json({HD:{dailyBar:{c:411,t:prior},prevDailyBar:{c:400,t:before}}});
  };
  const runtime=new Miniflare(convertV4MiniflareOptions({modules:true,scriptPath:fileURLToPath(new URL('./worker.mjs',import.meta.url)),
    compatibilityDate:'2026-10-30',durableObjects:{MARKET_CACHE:{className:'MarketDataCache',useSQLite:true}},
    bindings:{PROXY_ACCESS_TOKEN:TOKEN,ALPACA_API_KEY_ID:'fixture-key',ALPACA_API_SECRET_KEY:'fixture-secret',MARKET_DISPLAY_LICENSE_CONFIRMED:'true'},outboundService}));
  const auth={headers:{Authorization:'Bearer '+TOKEN}},url='https://worker.example/v1/quotes?symbols=HD';
  try {
    assert.equal((await runtime.dispatchFetch(url)).status,401);assert.equal(seen.length,0);
    let response=await runtime.dispatchFetch(url,auth);assert.equal(response.status,200);
    const first=await response.json(),q=first.quotes[0];
    assert.equal(q.price,411);assert.equal(q.previousClose,400);assert.equal(q.updated,prior);
    assert.equal(q.freshness,'PREVIOUS_CLOSE');assert.equal(q.feedLabel,'IEX only');
    response=await runtime.dispatchFetch(url,auth);assert.deepEqual((await response.json()).quotes,first.quotes);
    assert.deepEqual(seen,['/v2/stocks/snapshots','/v2/clock']);
    assert.equal((await runtime.dispatchFetch(url,{headers:{Authorization:'Bearer '+'b'.repeat(64)}})).status,401);
    assert.equal(seen.length,2);
  } finally {await runtime.dispose();}
});
