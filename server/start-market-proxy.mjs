import { pathToFileURL } from 'node:url';
import { MarketService, createServer } from './market-proxy.mjs';
import { AlpacaMarketService } from './alpaca-market-data.mjs';

export function configuredService(env=process.env) {
  const shared={requestsPerMinute:Number(env.MARKET_REQUESTS_PER_MINUTE ?? (env.MARKET_PROVIDER==='massive'?5:60)),
    allowedSymbols:env.MARKET_ALLOWED_SYMBOLS ? new Set(env.MARKET_ALLOWED_SYMBOLS.split(',')) : undefined};
  switch(env.MARKET_PROVIDER ?? 'alpaca') {
    case 'alpaca': return new AlpacaMarketService({...shared,apiKey:env.ALPACA_API_KEY_ID,secretKey:env.ALPACA_API_SECRET_KEY,
      feed:env.ALPACA_FEED ?? 'iex',tradingBaseUrl:env.ALPACA_TRADING_BASE_URL ?? 'https://api.alpaca.markets'});
    case 'massive': return new MarketService({...shared,apiKey:env.MASSIVE_API_KEY,freshness:env.MARKET_FRESHNESS ?? 'PREVIOUS_CLOSE',indices:env.MARKET_INDICES_ENABLED==='true'});
    default: throw new Error('MARKET_PROVIDER must be alpaca or massive');
  }
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  if(process.env.MARKET_DISPLAY_LICENSE_CONFIRMED!=='true') throw new Error('Confirm provider display rights with MARKET_DISPLAY_LICENSE_CONFIRMED=true');
  createServer(configuredService()).listen(Number(process.env.PORT ?? 8787),'127.0.0.1',()=>process.stdout.write('Market proxy listening on loopback\n'));
}
