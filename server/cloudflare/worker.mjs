// Web-standard runtime only: no Node server, timers, background queues or dependencies.
const DEFAULT_SYMBOLS='HD,AAPL,MSFT,GOOGL,AMZN,NVDA,WMT,COST,TGT,JPM,BAC,BRK.B,UNH,JNJ,LLY,XOM,CVX,CAT,GE,BA';
const INDICES=['I:SPX','I:DJI','I:COMP'];
const RANGES={'1D':0,'1W':7,'1M':31,'3M':93,'1Y':366};
const FEEDS={iex:{freshness:'REALTIME',feedLabel:'IEX only'},sip:{freshness:'REALTIME',feedLabel:'SIP'},delayed_sip:{freshness:'DELAYED',feedLabel:'SIP',delayMinutes:15}};
const positive=n=>typeof n==='number' && Number.isFinite(n) && n>0 ? n : null;
const time=s=>typeof s==='string' && /T.*(?:Z|[+-]\d{2}:\d{2})$/.test(s) && Number.isFinite(Date.parse(s)) ? new Date(s).toISOString() : null;
const date=n=>new Intl.DateTimeFormat('en-CA',{timeZone:'America/New_York',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(n));
const json=(body,status=200,headers={})=>Response.json(body,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff',...headers}});
const unavailable=symbol=>({symbol,price:null,freshness:'UNAVAILABLE',updated:null});
class InputError extends Error {}
class ProviderError extends Error {
  constructor(retrySeconds=60) {super('Market provider unavailable');this.retrySeconds=retrySeconds;}
}

export class MarketDataService {
  constructor(storage,env,{fetcher=fetch,now=()=>Date.now()}={}) {
    this.storage=storage;this.env=env;this.fetcher=fetcher;this.now=now;
    // One named Durable Object serializes clients; storage survives isolate restarts.
    this.tail=Promise.resolve();
  }
  fetch(request) {
    const result=this.tail.then(()=>this.handle(request));
    this.tail=result.catch(()=>{});return result;
  }
  symbols(value,indices=false) {
    const list=[...new Set((value ?? '').split(',').filter(Boolean))].sort();
    const allowed=indices ? INDICES : (this.env.MARKET_ALLOWED_SYMBOLS ?? DEFAULT_SYMBOLS).split(',').map(s=>s.trim()).filter(Boolean);
    if(list.length>(indices?3:52) || list.some(s=>!allowed.includes(s) || !indices && !/^[A-Z][A-Z0-9]{0,9}(\.[A-Z])?$/.test(s))) throw new InputError();
    return list;
  }
  async upstream(provider,path,params={}) {
    const now=this.now(), key=`quota:${provider}`;
    const budget=await this.storage.get(key) ?? {times:[],blockedUntil:0};
    budget.times=budget.times.filter(t=>t>now-60_000);
    const limit=Number(provider==='alpaca' ? this.env.ALPACA_REQUESTS_PER_MINUTE ?? 60 : this.env.MASSIVE_REQUESTS_PER_MINUTE ?? 5);
    if(!Number.isInteger(limit) || limit<1 || limit>200) throw new ProviderError(60);
    if(budget.blockedUntil>now) throw new ProviderError(Math.ceil((budget.blockedUntil-now)/1000));
    if(budget.times.length>=limit) throw new ProviderError(Math.ceil((budget.times[0]+60_100-now)/1000));
    budget.times.push(now);await this.storage.put(key,budget);
    const trading=this.env.ALPACA_TRADING_BASE_URL ?? 'https://api.alpaca.markets';
    if(!['https://api.alpaca.markets','https://paper-api.alpaca.markets'].includes(trading)) throw new ProviderError();
    const url=new URL(path,provider==='massive'?'https://api.massive.com':path==='/v2/clock'?trading:'https://data.alpaca.markets');
    for(const [k,v] of Object.entries(params)) url.searchParams.set(k,String(v));
    const headers=provider==='alpaca'?{'APCA-API-KEY-ID':this.env.ALPACA_API_KEY_ID,'APCA-API-SECRET-KEY':this.env.ALPACA_API_SECRET_KEY}:{Authorization:`Bearer ${this.env.MASSIVE_API_KEY}`};
    let response;
    try {response=await this.fetcher(url,{method:'GET',redirect:'error',headers,signal:AbortSignal.timeout(8000)});}
    catch {throw new ProviderError();}
    const responseNow=this.now(),retry=response.headers.get('Retry-After');
    const retryAt=retry && /^\d+$/.test(retry)?responseNow+Number(retry)*1000:Date.parse(retry ?? '');
    const resetAt=Number(response.headers.get('X-RateLimit-Reset'))*1000;
    if(response.status===429 || response.headers.get('X-RateLimit-Remaining')==='0') {
      budget.blockedUntil=Math.max(responseNow+60_000,Number.isFinite(retryAt)?retryAt:0,Number.isFinite(resetAt)?resetAt:0)+100;
      await this.storage.put(key,budget);
    }
    if(!response.ok) throw new ProviderError(Math.max(60,Math.ceil((budget.blockedUntil-responseNow)/1000)));
    let body;try {body=await response.json();} catch {throw new ProviderError();}
    if(!body || typeof body!=='object' || body.status==='ERROR' || body.status==='NOT_AUTHORIZED' || 'code' in body && 'message' in body) throw new ProviderError();
    return body;
  }
  async cached(key,ttl,load) {
    key=`cache:${key}`;const old=await this.storage.get(key),now=this.now();
    if(old && old.due>now) return {...old,retrySeconds:Math.max(60,Math.ceil((old.due-now)/1000))};
    try {
      const value=await load(old?.value);
      const entry={value,due:this.now()+ttl,failures:0,stale:false};await this.storage.put(key,entry);return {...entry,retrySeconds:60};
    } catch(error) {
      const failures=(old?.failures ?? 0)+1;
      const retrySeconds=Math.max(error.retrySeconds ?? 60,Math.min(3600,60*2**Math.min(failures-1,6)));
      const entry={value:old?.value ?? null,due:this.now()+retrySeconds*1000,failures,stale:true};await this.storage.put(key,entry);return {...entry,retrySeconds};
    }
  }
  async indexQuotes(symbols) {
    if(!this.env.MASSIVE_API_KEY) return {quotes:symbols.map(unavailable),staleSymbols:[],retrySeconds:900,warning:symbols.length?'Cash indices unavailable: optional Massive index credentials are not configured.':null};
    const quotes=[],staleSymbols=[];let failed=false,retrySeconds=60;
    // Completed prior Eastern calendar date; provider bars choose actual trading days.
    const today=date(this.now()),end=new Date(`${today}T12:00:00Z`);end.setUTCDate(end.getUTCDate()-1);
    for(const symbol of symbols) {
      const result=await this.cached(`index:${symbol}`,900_000,async()=>{
        const body=await this.upstream('massive',`/v2/aggs/ticker/${encodeURIComponent(symbol)}/range/1/day/${date(this.now()-14*86400_000)}/${end.toISOString().slice(0,10)}`,{sort:'asc',limit:100});
        if(!Array.isArray(body.results) && body.resultsCount!==0) throw new ProviderError();
        const bars=(body.results ?? []).filter(b=>positive(b.c) && Number.isFinite(b.t) && b.t>0).sort((a,b)=>a.t-b.t),last=bars.at(-1);
        return last?{symbol,price:last.c,previousClose:positive(bars.at(-2)?.c),high:positive(last.h),low:positive(last.l),updated:new Date(last.t).toISOString(),freshness:'PREVIOUS_CLOSE',feedLabel:'Massive index EOD'}:unavailable(symbol);
      });
      quotes.push(result.value ?? unavailable(symbol));failed ||= result.stale;
      if(result.stale && result.value?.price) staleSymbols.push(symbol);
      retrySeconds=Math.max(retrySeconds,result.retrySeconds);
    }
    return {quotes,staleSymbols,retrySeconds,warning:failed?'Index feed unavailable; retained values keep their original timestamps.':null};
  }
  async quotes(symbols,indices,feed) {
    const info=FEEDS[feed],key=`quotes:${feed}:${symbols.join(',')}`;
    const result=await this.cached(key,60_000,async old=>{
      const body=await this.upstream('alpaca','/v2/stocks/snapshots',{symbols:symbols.join(','),feed,currency:'USD'});
      const retained=[];
      const quotes=symbols.map(symbol=>{
        const item=body[symbol],trade=item?.latestTrade,minute=item?.minuteBar;
        const point=positive(trade?.p) && time(trade?.t)?{price:trade.p,updated:time(trade.t)}:positive(minute?.c) && time(minute?.t)?{price:minute.c,updated:time(minute.t)}:null;
        if(!point) {
          const previous=old?.quotes.find(q=>q.symbol===symbol && q.price);
          if(previous) {retained.push(symbol);return previous;}
          return unavailable(symbol);
        }
        return {symbol,...point,previousClose:positive(item.prevDailyBar?.c),high:positive(item.dailyBar?.h),low:positive(item.dailyBar?.l),volume:item.dailyBar?.v ?? null,...info};
      });
      if(quotes.every(q=>q.price===null)) throw new ProviderError();
      return {quotes,staleSymbols:retained};
    });
    const clockHost=this.env.ALPACA_TRADING_BASE_URL ?? 'https://api.alpaca.markets';
    const clock=await this.cached(`clock:${clockHost}`,60_000,async()=>{
      const c=await this.upstream('alpaca','/v2/clock');
      if(!time(c.timestamp) || typeof c.is_open!=='boolean') throw new ProviderError();
      return {session:c.is_open?'OPEN':'CLOSED',statusUpdated:time(c.timestamp)};
    });
    const index=await this.indexQuotes(indices);
    const quotes=result.value?.quotes ?? symbols.map(unavailable);
    const staleSymbols=[...(result.stale?quotes.filter(q=>q.price!==null).map(q=>q.symbol):result.value?.staleSymbols ?? []),...index.staleSymbols];
    const status=clock.value && this.now()-Date.parse(clock.value.statusUpdated)>=0 && this.now()-Date.parse(clock.value.statusUpdated)<=120_000?clock.value:{session:'UNKNOWN',statusUpdated:clock.value?.statusUpdated ?? null};
    return {provider:`Alpaca · ${info.feedLabel}`,quotes:[...quotes,...index.quotes],...status,staleSymbols,pending:false,
      refreshAfterSeconds:Math.max(result.retrySeconds,clock.retrySeconds),
      warning:[result.stale?'Stock feed unavailable; retained prices keep their original timestamps.':staleSymbols.length?'Some quotes are stale.':null,clock.stale?'Market status unavailable.':null,index.warning].filter(Boolean).join(' ') || null};
  }
  async history(symbol,range,feed) {
    const intraday=range==='1D',info=FEEDS[feed],today=date(this.now());
    const result=await this.cached(`history:${feed}:${symbol}:${range}:${intraday?today:'daily'}`,intraday?300_000:21_600_000,async()=>{
      let token=null,points=[];const seen=new Set();
      const end=new Date(this.now()-(info.delayMinutes ?? 0)*60_000-(info.delayMinutes?1000:0)).toISOString();
      // Never publish partial pages. A bounded request also fits Workers Free subrequest limits.
      for(let page=0;page<10;page++) {
        if(seen.has(token)) throw new ProviderError();seen.add(token);
        const body=await this.upstream('alpaca','/v2/stocks/bars',{symbols:symbol,timeframe:intraday?'5Min':'1Day',start:date(this.now()-RANGES[range]*86400_000),end,limit:1000,sort:'asc',adjustment:'split',feed:feed==='delayed_sip'?'sip':feed,currency:'USD',...(token?{page_token:token}:{})});
        if(!('bars' in body) || body.bars!==null && (typeof body.bars!=='object' || Array.isArray(body.bars)) || body.bars?.[symbol]!=null && !Array.isArray(body.bars[symbol])) throw new ProviderError();
        points.push(...(body.bars?.[symbol] ?? []).filter(p=>positive(p.c) && time(p.t)).map(p=>({time:time(p.t),close:p.c})));
        token=body.next_page_token;
        if(token!=null && typeof token!=='string') throw new ProviderError();
        if(!token) {
          points=[...new Map(points.filter(p=>!intraday || date(Date.parse(p.time))===today).map(p=>[p.time,p])).values()].sort((a,b)=>Date.parse(a.time)-Date.parse(b.time));
          return {points,...info,updated:new Date(this.now()).toISOString(),pending:false,message:points.length<2?'Historical data unavailable for this feed or session.':null};
        }
      }
      throw new ProviderError();
    });
    return {...(result.value ?? {points:[],freshness:'UNAVAILABLE',pending:false}),...(result.stale?{message:'History refresh unavailable; retained history may be stale.',stale:true}:{}),refreshAfterSeconds:result.retrySeconds};
  }
  async handle(request) {
    try {
      const url=new URL(request.url);
      if(request.method!=='GET') return json({error:'Method not allowed'},405,{Allow:'GET'});
      if(url.pathname==='/health') return json({service:'OpenTicker market proxy',configured:!!(this.env.ALPACA_API_KEY_ID && this.env.ALPACA_API_SECRET_KEY && FEEDS[this.env.ALPACA_FEED ?? 'iex'] && this.env.MARKET_DISPLAY_LICENSE_CONFIRMED==='true'),indicesConfigured:!!this.env.MASSIVE_API_KEY});
      if(!['/v1/quotes','/v1/history','/v1/indices'].includes(url.pathname)) return json({error:'Not found'},404);
      const feed=this.env.ALPACA_FEED ?? 'iex';
      if(!FEEDS[feed]) return json({error:'Invalid server feed configuration'},503);
      if(this.env.MARKET_DISPLAY_LICENSE_CONFIRMED!=='true') return json({error:'Confirm provider display rights in server configuration'},503);
      if(url.pathname==='/v1/indices') {
        const symbols=this.symbols(url.searchParams.get('symbols') ?? INDICES.join(','),true);
        const result=await this.indexQuotes(symbols);return json({...result,provider:'Massive indices',refreshAfterSeconds:result.retrySeconds,pending:false});
      }
      if(!this.env.ALPACA_API_KEY_ID || !this.env.ALPACA_API_SECRET_KEY) return json({error:'Stock data not configured'},503,{'Retry-After':'60'});
      if(url.pathname==='/v1/quotes') {
        const symbols=this.symbols(url.searchParams.get('symbols'));
        if(!symbols.length) throw new InputError();
        return json(await this.quotes(symbols,this.symbols(url.searchParams.get('indices'),true),feed));
      }
      const symbols=this.symbols(url.searchParams.get('symbol')),range=url.searchParams.get('range');
      if(symbols.length!==1 || !Object.hasOwn(RANGES,range)) throw new InputError();
      return json(await this.history(symbols[0],range,feed));
    } catch(error) {
      return error instanceof InputError?json({error:'Invalid or unapproved symbol, index or range'},400):json({error:'Market service temporarily unavailable'},503,{'Retry-After':'60'});
    }
  }
}

export class MarketDataCache {
  constructor(ctx,env) {this.service=new MarketDataService(ctx.storage,env);}
  fetch(request) {return this.service.fetch(request);}
}

export default {
  async fetch(request,env) {
    try {
      const id=env.MARKET_CACHE.idFromName('openticker-market-v1');
      return await env.MARKET_CACHE.get(id).fetch(request);
    } catch {return json({error:'Market service temporarily unavailable'},503,{'Retry-After':'60'});}
  }
};
