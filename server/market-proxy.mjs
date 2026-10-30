import http from 'node:http';
import { pathToFileURL } from 'node:url';

const positive = value => typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : null;
const iso = value => Number.isFinite(value) && value > 0 ? new Date(value).toISOString() : null;
export const easternDate = date => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/New_York', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
export const sessionFrom = body => body?.exchanges?.nyse === 'open' ? 'OPEN' : ['closed','extended-hours'].includes(body?.exchanges?.nyse) ? 'CLOSED' : 'UNKNOWN';
export function mapBars(body) {
  if (!Array.isArray(body.results) && body.resultsCount !== 0) throw new Error('Invalid aggregates');
  return [...new Map((body.results ?? []).filter(p => positive(p.c) && iso(p.t)).map(p => [p.t, p])).values()].sort((a,b) => a.t-b.t);
}
export function endOfDayQuote(symbol, body) {
  const bars = mapBars(body), last = bars.at(-1), previous = bars.at(-2);
  return { symbol, price: positive(last?.c), previousClose: positive(previous?.c), high: positive(last?.h), low: positive(last?.l),
    volume: last?.v ?? null, updated: iso(last?.t), freshness: last ? 'PREVIOUS_CLOSE' : 'UNAVAILABLE' };
}
export function snapshotQuote(item, freshness) {
  // Use the most recent minute aggregate, which is available on delayed snapshot plans.
  const minute = item.min ?? {}, previous = item.prevDay ?? {};
  const price = positive(minute.c);
  return { symbol: item.ticker, price, previousClose: positive(previous.c), high: positive(item.day?.h), low: positive(item.day?.l),
    volume: item.day?.v ?? null, updated: iso(minute.t), freshness: price ? freshness : 'UNAVAILABLE',
    delayMinutes: freshness === 'DELAYED' ? 15 : null };
}

/** Single serialized upstream queue, shared across quotes, indices, status and charts. */
export class MarketService {
  constructor({ apiKey, freshness = 'PREVIOUS_CLOSE', requestsPerMinute = 5, indices = false, fetcher = fetch,
    now = () => Date.now(), schedule = (fn, ms) => setTimeout(fn, ms), allowedSymbols } = {}) {
    if (!apiKey) throw new Error('MASSIVE_API_KEY is required');
    if (!['PREVIOUS_CLOSE','DELAYED','REALTIME'].includes(freshness)) throw new Error('Invalid MARKET_FRESHNESS');
    if (!Number.isInteger(requestsPerMinute) || requestsPerMinute<1 || requestsPerMinute>1000) throw new Error('Invalid request quota');
    this.apiKey=apiKey; this.freshness=freshness; this.indices=indices; this.fetcher=fetcher; this.now=now; this.schedule=schedule;
    this.spacing=Math.ceil(60000/requestsPerMinute)+50; this.allowedSymbols=allowedSymbols;
    this.cache=new Map(); this.jobs=new Map(); this.queue=[]; this.running=false; this.nextRequest=0;
  }
  async upstream(path, params={}) {
    const url = new URL(path,'https://api.massive.com');
    for(const [key,value] of Object.entries(params)) url.searchParams.set(key,String(value));
    const response=await this.fetcher(url,{headers:{Authorization:`Bearer ${this.apiKey}`},signal:AbortSignal.timeout(15000)});
    if(!response.ok) throw new Error(`Upstream HTTP ${response.status}`);
    const body=await response.json();
    if(['ERROR','NOT_AUTHORIZED'].includes(body.status)) throw new Error('Upstream unavailable');
    return body;
  }
  request(key, ttl, action) {
    const previous=this.cache.get(key);
    if((!previous || this.now()>=previous.due) && !this.jobs.has(key)) {
      const task={key,ttl,action}; this.jobs.set(key,task); this.queue.push(task); this.pump();
    }
    return previous?.value;
  }
  pump() {
    if(this.running || !this.queue.length) return;
    this.running=true;
    this.schedule(async()=>{
      const task=this.queue.shift(), old=this.cache.get(task.key);
      this.nextRequest=this.now()+this.spacing;
      try {
        const value=await task.action();
        this.cache.set(task.key,{value,due:this.now()+task.ttl,failures:0,error:false});
      } catch {
        const failures=(old?.failures ?? 0)+1;
        this.cache.set(task.key,{value:old?.value,due:this.now()+Math.min(3600000,60000*2**Math.min(failures-1,6)),failures,error:true});
      } finally {
        this.jobs.delete(task.key); this.running=false; this.pump();
      }
    },Math.max(0,this.nextRequest-this.now()));
  }
  validate(symbols) {
    if(symbols.length<1 || symbols.length>52 || symbols.some(s=> !/^[A-Z][A-Z0-9]{0,9}(\.[A-Z])?$/.test(s) || (this.allowedSymbols && !this.allowedSymbols.has(s)))) {
      throw new Error('Invalid or unapproved symbol');
    }
    return [...new Set(symbols)];
  }
  dates(days, completed = false) {
    const date=new Date(this.now());
    const end=easternDate(date);
    const completedEnd=new Date(`${end}T12:00:00Z`); completedEnd.setUTCDate(completedEnd.getUTCDate()-1);
    return [easternDate(new Date(date.getTime()-days*86400000)),completed ? completedEnd.toISOString().slice(0,10) : end];
  }
  quotes(symbols, indices=[]) {
    symbols=this.validate(symbols);
    if(indices.some(s=> !['I:SPX','I:DJI','I:COMP'].includes(s))) throw new Error('Invalid index');
    const keys=['status'];
    const status=this.request('status',60000,async()=>{
      const body=await this.upstream('/v1/marketstatus/now');
      return {session:sessionFrom(body),statusUpdated:body.serverTime ?? null};
    });
    const quotes=[];
    if(this.freshness==='PREVIOUS_CLOSE') {
      for(const symbol of symbols) {
        const key=`quote:${symbol}`; keys.push(key);
        const quote=this.request(key,900000,async()=>{
          const [start,end]=this.dates(14,true);
          const body=await this.upstream(`/v2/aggs/ticker/${encodeURIComponent(symbol)}/range/1/day/${start}/${end}`,{adjusted:true,sort:'asc',limit:100});
          return endOfDayQuote(symbol,body);
        });
        quotes.push(quote ?? {symbol,price:null,freshness:'UNAVAILABLE'});
      }
    } else {
      const key=`snapshots:${symbols.join(',')}`; keys.push(key);
      const snapshot=this.request(key,60000,async()=>{
        const body=await this.upstream('/v2/snapshot/locale/us/markets/stocks/tickers',{tickers:symbols.join(',')});
        if(!Array.isArray(body.tickers)) throw new Error('Missing snapshots');
        return body.tickers.map(item=>snapshotQuote(item,this.freshness));
      });
      quotes.push(...(snapshot ?? symbols.map(symbol=>({symbol,price:null,freshness:'UNAVAILABLE'}))));
    }
    for(const symbol of indices) {
      if(!this.indices) { quotes.push({symbol,price:null,freshness:'UNAVAILABLE'}); continue; }
      const key=`index:${symbol}`; keys.push(key);
      const quote=this.request(key,900000,async()=>{
        const [start,end]=this.dates(14,true);
        return endOfDayQuote(symbol,await this.upstream(`/v2/aggs/ticker/${encodeURIComponent(symbol)}/range/1/day/${start}/${end}`,{sort:'asc',limit:100}));
      });
      quotes.push(quote ?? {symbol,price:null,freshness:'UNAVAILABLE'});
    }
    const pending=keys.some(key=>this.jobs.has(key));
    const failed=keys.some(key=>this.cache.get(key)?.error);
    const staleSymbols=keys.filter(key=>this.cache.get(key)?.error).flatMap(key=>key.startsWith('quote:') || key.startsWith('index:') ? [key.slice(key.indexOf(':')+1)] : key.startsWith('snapshots:') ? symbols : []);
    return {provider:'Massive',quotes,...(status ?? {session:'UNKNOWN',statusUpdated:null}),pending,
      staleSymbols,
      refreshAfterSeconds:60,warning:failed ? 'Some provider requests failed; retained quotes keep their original timestamps.' : pending ? 'Market data is loading within the configured provider quota.' : null};
  }
  history(symbol, range) {
    this.validate([symbol]);
    const days={'1D':0,'1W':7,'1M':31,'3M':93,'1Y':366}[range];
    if(days===undefined) throw new Error('Invalid range');
    const key=`history:${symbol}:${range}`;
    const history=this.request(key,range==='1D'?300000:21600000,async()=>{
      const [start,end]=this.dates(days,range!=='1D' && this.freshness==='PREVIOUS_CLOSE');
      const intraday=range==='1D';
      const body=await this.upstream(`/v2/aggs/ticker/${encodeURIComponent(symbol)}/range/${intraday?'5/minute':'1/day'}/${start}/${end}`,{adjusted:true,sort:'asc',limit:5000});
      // Current session only for 1D; no synthetic fallback or interpolation.
      const points=mapBars(body).map(p=>({time:iso(p.t),close:p.c}));
      return {points,freshness:this.freshness,updated:new Date(this.now()).toISOString(),
        message:points.length<2 ? intraday?'Intraday data unavailable for this feed or session.':'Historical data unavailable.' : null};
    });
    if(history) return {...history,message:this.cache.get(key)?.error?'Chart refresh failed; retained historical data may be stale.':history.message};
    return {points:[],pending:true,freshness:'UNAVAILABLE',message:range==='1D'?'Intraday data unavailable or loading for this feed.':'Historical data unavailable or loading.'};
  }
}

export function createServer(service) {
  return http.createServer((req,res)=>{
    res.setHeader('Content-Type','application/json'); res.setHeader('Cache-Control','no-store');
    try {
      if(req.method!=='GET') { res.writeHead(405);res.end('{}');return; }
      const url=new URL(req.url,'http://localhost');
      const list=key=>(url.searchParams.get(key) ?? '').split(',').filter(Boolean);
      let result;
      if(url.pathname==='/v1/quotes') result=service.quotes(list('symbols'),list('indices'));
      else if(url.pathname==='/v1/history') result=service.history(url.searchParams.get('symbol') ?? '',url.searchParams.get('range'));
      else {res.writeHead(404);res.end('{}');return;}
      res.end(JSON.stringify(result));
    } catch { res.writeHead(400);res.end(JSON.stringify({error:'Invalid market data request'})); }
  });
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  if(process.env.MARKET_DISPLAY_LICENSE_CONFIRMED!=='true') throw new Error('Confirm provider display rights with MARKET_DISPLAY_LICENSE_CONFIRMED=true');
  const service=new MarketService({apiKey:process.env.MASSIVE_API_KEY,freshness:process.env.MARKET_FRESHNESS ?? 'PREVIOUS_CLOSE',
    requestsPerMinute:Number(process.env.MARKET_REQUESTS_PER_MINUTE ?? 5),indices:process.env.MARKET_INDICES_ENABLED==='true',
    allowedSymbols:process.env.MARKET_ALLOWED_SYMBOLS ? new Set(process.env.MARKET_ALLOWED_SYMBOLS.split(',')) : undefined});
  // Keep this behind an HTTPS reverse proxy on a private network/VPN, or add approved user authentication there.
  createServer(service).listen(Number(process.env.PORT ?? 8787),'127.0.0.1',()=>process.stdout.write('Market proxy listening on loopback\n'));
}
