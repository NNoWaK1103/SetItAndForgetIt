import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { AlpacaMarketService, alpacaQuote, alpacaClock, alpacaBars, alpacaBarsForSymbol } from './alpaca-market-data.mjs';
import { configuredService } from './start-market-proxy.mjs';

const fixture=JSON.parse(readFileSync(new URL('./fixtures/alpaca.json',import.meta.url),'utf8'));
const now=Date.parse('2026-10-29T14:00:00Z');
const credentials={apiKey:'fixture-key',secretKey:'fixture-secret'};
function harness(options={}) {
  let time=now;const pending=[],requests=[];
  let responder=async url=>url.pathname==='/v2/clock' ? fixture.clock : url.pathname.endsWith('/snapshots') ? fixture.snapshots : fixture.history;
  const service=new AlpacaMarketService({...credentials,now:()=>time,schedule:(fn,delay)=>pending.push({fn,at:time+delay}),
    fetcher:async(url,init)=>{
      requests.push({url,init,time});
      const value=await responder(url,init);
      return value?.httpResponse ?? {ok:true,status:200,json:async()=>value};
    },...options});
  return {service,requests,pending,respond:fn=>{responder=fn;},advance:value=>{time=value;},time:()=>time,
    run:async()=>{const job=pending.shift();assert.ok(job,'expected a queued request');time=Math.max(time,job.at);await job.fn();}};
}
test('Alpaca snapshots preserve prices and label IEX single-exchange coverage',()=>{
  const q=alpacaQuote('HD',fixture.snapshots.HD);
  assert.equal(q.price,410.25);assert.equal(q.previousClose,400);assert.equal(q.high,412);assert.equal(q.low,398);assert.equal(q.volume,123456);
  assert.equal(q.updated,'2026-10-29T13:59:59.123Z');assert.equal(q.freshness,'REALTIME');assert.equal(q.feedLabel,'IEX only');
  assert.equal(alpacaQuote('BRK.B',fixture.snapshots['BRK.B']).price,500);
  assert.equal(alpacaQuote('HD',null).price,null);
  assert.equal(alpacaQuote('HD',{latestTrade:{p:410.25}}).freshness,'UNAVAILABLE');
  assert.equal(alpacaQuote('HD',{latestTrade:{p:-1,t:'2026-10-29T13:59:00Z'}}).price,null);
});
test('SIP and delayed SIP are explicit feed choices with matching freshness',()=>{
  assert.equal(alpacaQuote('HD',fixture.snapshots.HD,'sip').feedLabel,'SIP');
  const delayed=alpacaQuote('HD',fixture.snapshots.HD,'delayed_sip');
  assert.equal(delayed.freshness,'DELAYED');assert.equal(delayed.delayMinutes,15);
  assert.throws(()=>new AlpacaMarketService({...credentials,feed:'automatic'}));
});
test('market clock uses the authoritative open flag on holidays and early closures',()=>{
  assert.deepEqual(alpacaClock(fixture.clock),{session:'OPEN',statusUpdated:'2026-10-29T14:00:00.000Z'});
  assert.equal(alpacaClock({is_open:false,timestamp:'2026-11-27T13:01:00-05:00'}).session,'CLOSED');
  assert.equal(alpacaClock({is_open:false,timestamp:'2026-12-25T10:00:00-05:00'}).session,'CLOSED');
  assert.equal(alpacaClock({is_open:true}).session,'UNKNOWN');assert.equal(alpacaClock({is_open:'true',timestamp:fixture.clock.timestamp}).session,'UNKNOWN');
});
test('read-only requests use the correct hosts and keep both credentials server side',async()=>{
  const h=harness();h.service.quotes(['HD','BRK.B'],['I:SPX']);h.service.history('HD','1D');
  h.service.quotes(['BRK.B','HD']);assert.equal(h.service.jobs.size,3);
  await h.run();await h.run();await h.run();
  assert.equal(h.requests[0].url.href,'https://api.alpaca.markets/v2/clock');
  assert.equal(h.requests[1].url.origin,'https://data.alpaca.markets');assert.equal(h.requests[1].url.pathname,'/v2/stocks/snapshots');
  assert.equal(h.requests[1].url.searchParams.get('feed'),'iex');assert.equal(h.requests[1].url.searchParams.get('symbols'),'HD,BRK.B');
  assert.ok(h.requests.every(r=>r.init.method==='GET' && r.init.redirect==='error'));
  assert.ok(h.requests.every(r=>r.init.headers['APCA-API-KEY-ID']==='fixture-key' && r.init.headers['APCA-API-SECRET-KEY']==='fixture-secret'));
  assert.ok(h.requests.slice(1).every((r,i)=>r.time-h.requests[i].time>=1000));
  const output=JSON.stringify(h.service.quotes(['HD','BRK.B'],['I:SPX']));
  assert.ok(!output.includes('fixture-key') && !output.includes('fixture-secret'));
  assert.equal(JSON.parse(output).quotes.find(q=>q.symbol==='I:SPX').freshness,'UNAVAILABLE');
  assert.equal(h.service.history('HD','1D').points.length,2);
});
test('paper keys can use the paper clock without changing the production data host',async()=>{
  const h=harness({tradingBaseUrl:'https://paper-api.alpaca.markets'});
  h.service.quotes(['HD']);await h.run();await h.run();
  assert.equal(h.requests[0].url.host,'paper-api.alpaca.markets');assert.equal(h.requests[1].url.host,'data.alpaca.markets');
  assert.throws(()=>new AlpacaMarketService({...credentials,tradingBaseUrl:'https://other.example'}));
});
test('bar parsing rejects error envelopes and invalid points without making up data',()=>{
  assert.equal(alpacaBarsForSymbol(fixture.history,'HD').points.length,2);
  assert.deepEqual(alpacaBarsForSymbol({bars:null},'HD').points,[]);
  assert.deepEqual(alpacaBarsForSymbol({bars:{}},'HD').points,[]);
  assert.throws(()=>alpacaBarsForSymbol({message:'forbidden'},'HD'));
  assert.throws(()=>alpacaBarsForSymbol({bars:[1,2]},'HD'));
  assert.equal(alpacaBars({bars:[{t:'invalid',c:1},{t:fixture.clock.timestamp,c:-1}]}).points.length,0);
});
test('historical pagination runs each page through the quota and publishes a complete chart',async()=>{
  const h=harness();h.respond(async url=>url.searchParams.has('page_token') ?
    {bars:{HD:[{t:'2026-10-29T13:40:00Z',c:410.25},{t:'2026-10-29T13:35:00Z',c:405}]},next_page_token:null} :
    {...fixture.history,next_page_token:'page-two'});
  assert.equal(h.service.history('HD','1D').pending,true);await h.run();
  assert.equal(h.service.history('HD','1D').points.length,0);await h.run();
  const chart=h.service.history('HD','1D');assert.equal(chart.points.length,3);assert.equal(chart.pending,false);
  assert.equal(h.requests[1].url.searchParams.get('page_token'),'page-two');
  assert.ok(h.requests[1].time-h.requests[0].time>=1000);
  assert.equal(chart.points.at(-1).close,410.25);assert.equal(chart.feedLabel,'IEX only');
});
test('delayed history queries documented SIP bars with an explicit fifteen-minute cutoff',async()=>{
  const h=harness({feed:'delayed_sip'});h.service.history('HD','1D');await h.run();
  const url=h.requests[0].url;
  assert.equal(url.searchParams.get('feed'),'sip');assert.equal(url.searchParams.get('timeframe'),'5Min');
  assert.ok(Date.parse(url.searchParams.get('end'))<=now-15*60000);
  assert.equal(h.service.history('HD','1D').freshness,'DELAYED');
});
test('all five ranges request real bars and keep caches separate',async()=>{
  const h=harness();
  for(const range of ['1D','1W','1M','3M','1Y']) {h.service.history('HD',range);await h.run();h.service.history('HD',range);}
  assert.equal(h.requests.length,5);assert.equal(h.requests[0].url.searchParams.get('timeframe'),'5Min');
  assert.ok(h.requests.slice(1).every(r=>r.url.searchParams.get('timeframe')==='1Day'));
  assert.equal(h.requests.at(-1).url.searchParams.get('start'),'2025-10-28');
});
test('failed snapshots retain data and obey server Retry-After instead of changing feeds',async()=>{
  const h=harness({feed:'sip'});h.service.quotes(['HD']);await h.run();await h.run();
  const original=h.service.quotes(['HD']).quotes[0];h.advance(now+62000);
  const reset=now+300000;
  h.respond(async()=>({httpResponse:{ok:false,status:429,headers:new Headers({'Retry-After':'240','X-RateLimit-Reset':String(reset/1000)})}}));
  h.service.quotes(['HD']);await h.run();
  assert.ok(h.pending[0].at>=reset);await h.run();
  const failed=h.service.quotes(['HD']);assert.deepEqual(failed.quotes[0],original);assert.deepEqual(failed.staleSymbols,['HD']);
  assert.ok(h.requests.filter(r=>r.url.pathname.endsWith('/snapshots')).every(r=>r.url.searchParams.get('feed')==='sip'));
});
test('failed chart refresh retains the prior chart and reports its age',async()=>{
  const h=harness();h.service.history('HD','1D');await h.run();const previous=h.service.history('HD','1D');
  h.advance(now+301000);h.respond(async()=>({httpResponse:{ok:false,status:403}}));
  h.service.history('HD','1D');await h.run();const failed=h.service.history('HD','1D');
  assert.deepEqual(failed.points,previous.points);assert.match(failed.message,/stale/);
});
test('a new Eastern date never relabels prior-day bars as the current 1D chart',async()=>{
  const h=harness();h.service.history('HD','1D');await h.run();assert.equal(h.service.history('HD','1D').points.length,2);
  h.advance(now+86400000);
  assert.equal(h.service.history('HD','1D').points.length,0);await h.run();
  const current=h.service.history('HD','1D');assert.equal(current.points.length,0);assert.match(current.message,/unavailable/);
});
test('repeated pagination tokens do not publish a truncated chart or load forever',async()=>{
  const h=harness();h.respond(async()=>({...fixture.history,next_page_token:'repeated'}));
  h.service.history('HD','1D');await h.run();h.service.history('HD','1D');await h.run();
  const failed=h.service.history('HD','1D');assert.equal(failed.points.length,0);assert.equal(failed.pending,false);assert.match(failed.message,/pagination unavailable/);
});
test('Alpaca is the configured default and never accepts credentials from a URL',()=>{
  assert.ok(configuredService({ALPACA_API_KEY_ID:'fixture-key',ALPACA_API_SECRET_KEY:'fixture-secret'}) instanceof AlpacaMarketService);
  assert.throws(()=>configuredService({}));assert.throws(()=>configuredService({MARKET_PROVIDER:'other'}));
  assert.throws(()=>new AlpacaMarketService({...credentials,tradingBaseUrl:'https://key:secret@api.alpaca.markets'}));
});
