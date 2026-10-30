import { MarketService, easternDate } from './market-proxy.mjs';

const positive = n => typeof n === 'number' && Number.isFinite(n) && n > 0 ? n : null;
const timestamp = value => typeof value === 'string' && /T.*(?:Z|[+-]\d{2}:\d{2})$/.test(value) && Number.isFinite(Date.parse(value)) ? new Date(value).toISOString() : null;
const FEEDS = {
  iex: { freshness: 'REALTIME', label: 'IEX only', delay: 0 },
  sip: { freshness: 'REALTIME', label: 'SIP', delay: 0 },
  delayed_sip: { freshness: 'DELAYED', label: 'SIP', delay: 15 },
};

export function alpacaClock(body) {
  const updated = timestamp(body?.timestamp);
  return { session: updated && typeof body?.is_open === 'boolean' ? body.is_open ? 'OPEN' : 'CLOSED' : 'UNKNOWN', statusUpdated: updated };
}
export function alpacaQuote(symbol, body, feed = 'iex') {
  const info = FEEDS[feed];
  if (!info) throw new Error('Invalid Alpaca feed');
  const trade = positive(body?.latestTrade?.p) && timestamp(body?.latestTrade?.t) ? body.latestTrade : null;
  const minute = positive(body?.minuteBar?.c) && timestamp(body?.minuteBar?.t) ? body.minuteBar : null;
  const price = trade ? trade.p : minute?.c ?? null;
  return { symbol, price, previousClose: positive(body?.prevDailyBar?.c),
    high: positive(body?.dailyBar?.h), low: positive(body?.dailyBar?.l), volume: body?.dailyBar?.v ?? null,
    updated: timestamp(trade?.t ?? minute?.t), freshness: price === null ? 'UNAVAILABLE' : info.freshness,
    delayMinutes: info.delay || null, feedLabel: info.label };
}
export function alpacaBars(body) {
  if (!body || (!Array.isArray(body.bars) && body.bars !== null)) throw new Error('Invalid Alpaca bars');
  if (body.next_page_token != null && typeof body.next_page_token !== 'string') throw new Error('Invalid page token');
  return { points: (body.bars ?? []).filter(p => positive(p.c) && timestamp(p.t))
    .map(p => ({time:timestamp(p.t),close:p.c})), next: body.next_page_token || null };
}
export function alpacaBarsForSymbol(body, symbol) {
  if(!body || !('bars' in body) || body.bars!==null && (typeof body.bars!=='object' || Array.isArray(body.bars))) throw new Error('Invalid Alpaca bars response');
  return alpacaBars({bars:body.bars?.[symbol] ?? [],next_page_token:body.next_page_token});
}

/** Read-only Alpaca adapter. Every HTTP call, including each history page, uses the shared queue. */
export class AlpacaMarketService extends MarketService {
  constructor({ apiKey, secretKey, feed = 'iex', tradingBaseUrl = 'https://api.alpaca.markets',
    requestsPerMinute = 60, ...options } = {}) {
    if (!apiKey || !secretKey) throw new Error('ALPACA_API_KEY_ID and ALPACA_API_SECRET_KEY are required on the server');
    if (!FEEDS[feed]) throw new Error('ALPACA_FEED must be iex, sip or delayed_sip');
    if (!['https://api.alpaca.markets','https://paper-api.alpaca.markets'].includes(tradingBaseUrl)) throw new Error('Invalid Alpaca trading host');
    super({ ...options, apiKey, requestsPerMinute, freshness:FEEDS[feed].freshness });
    this.secretKey=secretKey; this.feed=feed; this.tradingBaseUrl=tradingBaseUrl;
    this.historyContexts=new Map(); this.validHistories=new Map(); this.historyGeneration=0;
  }
  async upstream(path, params = {}) {
    const url=new URL(path,path==='/v2/clock' ? this.tradingBaseUrl : 'https://data.alpaca.markets');
    for (const [key,value] of Object.entries(params)) url.searchParams.set(key,String(value));
    const response=await this.fetcher(url,{method:'GET',redirect:'error',headers:{'APCA-API-KEY-ID':this.apiKey,'APCA-API-SECRET-KEY':this.secretKey},signal:AbortSignal.timeout(15000)});
    const reset=Number(response.headers?.get('X-RateLimit-Reset'))*1000;
    const retryHeader=response.headers?.get('Retry-After');
    const retry=retryHeader == null ? NaN : /^\d+$/.test(retryHeader) ? this.now()+Number(retryHeader)*1000 : Date.parse(retryHeader);
    if(response.status===429 || response.headers?.get('X-RateLimit-Remaining')==='0') {
      const deadline=Math.max(Number.isFinite(reset)?reset:0,Number.isFinite(retry)?retry:0,this.now()+60000);
      this.nextRequest=Math.max(this.nextRequest,deadline+100);
    }
    if(!response.ok) throw new Error(`Alpaca HTTP ${response.status}`);
    const body=await response.json();
    if(!body || typeof body!=='object' || 'code' in body && 'message' in body) throw new Error('Alpaca unavailable');
    return body;
  }
  quotes(symbols, indices = []) {
    symbols=this.validate(symbols);
    if(indices.some(s=>!['I:SPX','I:DJI','I:COMP'].includes(s))) throw new Error('Invalid index');
    const status=this.request('alpaca:clock',60000,async()=>alpacaClock(await this.upstream('/v2/clock')));
    const key=`alpaca:snapshots:${[...symbols].sort().join(',')}`;
    const quotes=this.request(key,60000,async()=>{
      const body=await this.upstream('/v2/stocks/snapshots',{symbols:symbols.join(','),feed:this.feed,currency:'USD'});
      return symbols.map(symbol=>alpacaQuote(symbol,body[symbol],this.feed));
    }) ?? symbols.map(symbol=>alpacaQuote(symbol,null,this.feed));
    const keys=['alpaca:clock',key];
    return { provider:`Alpaca · ${FEEDS[this.feed].label}`,quotes:[...quotes,...indices.map(symbol=>({symbol,price:null,freshness:'UNAVAILABLE'}))],
      ...(status ?? {session:'UNKNOWN',statusUpdated:null}), refreshAfterSeconds:60,
      pending:keys.some(k=>this.jobs.has(k)),staleSymbols:this.cache.get(key)?.error ? symbols : [],
      warning:keys.some(k=>this.cache.get(k)?.error) ? 'Alpaca refresh failed; retained quotes keep their original timestamps.'
        : keys.some(k=>this.jobs.has(k)) ? 'Alpaca market data is loading.' : null };
  }
  history(symbol, range) {
    this.validate([symbol]);
    const days={'1D':0,'1W':7,'1M':31,'3M':93,'1Y':366}[range];
    if(days===undefined) throw new Error('Invalid range');
    const info=FEEDS[this.feed], intraday=range==='1D', ttl=intraday?300000:21600000;
    const id=`${symbol}:${range}`, today=easternDate(new Date(this.now()));
    let context=this.historyContexts.get(id);
    if(!context || context.complete && this.now()-context.created>=ttl || intraday && context.date!==today) {
      if(intraday && context && context.date!==today) this.validHistories.delete(id);
      if(context) for(const key of context.keys) if(!this.jobs.has(key)) this.cache.delete(key);
      context={generation:++this.historyGeneration,created:this.now(),date:today,keys:new Set(),complete:false,
        start:easternDate(new Date(this.now()-days*86400000)),end:new Date(this.now()-info.delay*60000-(info.delay?1000:0)).toISOString()};
      this.historyContexts.set(id,context);
    }
    const retained=this.validHistories.get(id);
    let token=null, points=[], complete=false, failed=false, updating=false, limited=false;
    const seen=new Set();
    for(let page=0;page<10;page++) {
      if(seen.has(token)) { failed=true;limited=true;break; }
      seen.add(token);
      const key=`alpaca:history:${id}:${context.generation}:${token ?? 'first'}`;
      context.keys.add(key);
      const pageToken=token;
      const value=this.request(key,ttl,async()=>alpacaBarsForSymbol(await this.upstream('/v2/stocks/bars',{
        symbols:symbol,timeframe:intraday?'5Min':'1Day',start:context.start,end:context.end,limit:1000,sort:'asc',
        adjustment:'split',feed:this.feed==='delayed_sip'?'sip':this.feed,currency:'USD',...(pageToken?{page_token:pageToken}:{})
      }),symbol));
      failed ||= this.cache.get(key)?.error===true;
      updating ||= this.jobs.has(key);
      if(!value) break;
      points.push(...value.points);token=value.next;
      if(!token) { complete=true;break; }
      if(page===9) { limited=true;failed=true; }
    }
    if(limited) {
      context.complete=true;
      return {...(retained ?? {points:[],freshness:'UNAVAILABLE'}),pending:false,message:'Historical pagination unavailable; retained chart data may be stale.'};
    }
    if(!complete) return { ...(retained ?? {points:[],freshness:'UNAVAILABLE'}),pending:true,
      message:failed ? 'Alpaca history unavailable; retained chart data may be stale.' : 'Historical data is loading or unavailable for this feed.' };
    context.complete=true;
    points=[...new Map(points.map(p=>[p.time,p])).values()].filter(p=>!intraday || easternDate(new Date(p.time))===today)
      .sort((a,b)=>Date.parse(a.time)-Date.parse(b.time));
    const result={points,freshness:info.freshness,feedLabel:info.label,updated:new Date(context.created).toISOString(),pending:updating,
      message:failed ? 'Alpaca history refresh failed; retained chart data may be stale.'
        : points.length<2 ? intraday?'Intraday data unavailable for this feed or session.':'Historical data unavailable.' : null};
    this.validHistories.set(id,result);
    return result;
  }
}
