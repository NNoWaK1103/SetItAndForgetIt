/** Real-APK regression checks. Run only on a disposable emulator with ANDROID_SERIAL set. */
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import assert from 'node:assert/strict';

const serial=process.env.ANDROID_SERIAL;
assert.match(serial ?? '',/^emulator-\d+$/,'Set ANDROID_SERIAL to a disposable emulator');
const adbPath=process.env.ADB ?? 'adb';
const output=process.env.PLAYBACK_TEST_OUTPUT ?? 'preview-output/playback-verification';
mkdirSync(output,{recursive:true});
const report={serial,defaultCycle:[],intervals:[],manualScreens:[],disabledScreens:[],sourceCommit:process.env.GITHUB_SHA ?? null};
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function adb(...args) {return execFileSync(adbPath,['-s',serial,...args],{timeout:args[0]==='install'?90000:20000,maxBuffer:8*1024*1024});}
function shell(...args) {return adb('shell',...args).toString().trim();}
const decode=value=>value.replace(/&quot;/g,'"').replace(/&apos;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(Number(n))).replace(/&amp;/g,'&');
function nodes() {
  for(let attempt=0;attempt<3;attempt++) {
    const result=shell('uiautomator','dump','--compressed','/sdcard/openticker-playback.xml');
    // A null-root dump can exit successfully and leave the previous XML file behind.
    if(!result.includes('dumped to:')) continue;
    const xml=shell('cat','/sdcard/openticker-playback.xml');
    writeFileSync(join(output,'last-ui.xml'),xml);
    return [...xml.matchAll(/<node\b([^>]*)>/g)].map(match=>Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(([,key,value])=>[key,decode(value)])));
  }
  throw new Error('Android UI hierarchy is unavailable after three fresh dumps');
}
function center(node) {
  const bounds=node.bounds?.match(/\d+/g)?.map(Number);
  assert.equal(bounds?.length,4,'UI element has bounds');
  return [(bounds[0]+bounds[2])/2,(bounds[1]+bounds[3])/2];
}
function isSelected(tree,node) {
  const [x,y]=center(node);
  return tree.some(n=>{
    // Tabs expose selected; Material filter chips expose checked on newer Android versions.
    if(n.selected!=='true' && n.checked!=='true') return false;
    const [left,top,right,bottom]=n.bounds.match(/\d+/g).map(Number);
    return x>=left && x<=right && y>=top && y<=bottom;
  });
}
function tap(node) {const [x,y]=center(node);shell('input','tap',String(Math.round(x)),String(Math.round(y)));}
async function tapText(label,{scroll=false}={}) {
  for(let attempt=0;attempt<(scroll?8:3);attempt++) {
    const node=nodes().find(n=>n.text===label);
    if(node) {tap(node);await pause(200);return;}
    if(scroll) shell('input','swipe','640','610','640','320','250');
    await pause(250);
  }
  throw new Error(`Missing UI button: ${label}`);
}
function screenshot(name) {writeFileSync(join(output,`${name}.png`),adb('exec-out','screencap','-p'));}
function screenPresent(tree,label) {return tree.some(n=>n['content-desc']===`${label} dashboard`);}
async function assertScreen(label) {
  for(let attempt=0;attempt<3;attempt++) {
    const tree=nodes();
    if(screenPresent(tree,label)) {
      const tab=tree.find(n=>n['content-desc']===`Show ${label} dashboard`);
      assert.ok(tab && isSelected(tree,tab),`${label} tab must be selected`);
      return;
    }
    await pause(300);
  }
  throw new Error(`${label} button did not display its dashboard`);
}
async function switchTo(label) {await tapText(label);await assertScreen(label);}
async function setToggle(label,checked) {
  const tree=nodes(), caption=tree.find(n=>n.text===label);
  assert.ok(caption,`Missing switch label ${label}`);
  const [,y]=center(caption);
  const control=tree.find(n=>n.checkable==='true' && Math.abs(center(n)[1]-y)<20);
  assert.ok(control,`Missing switch for ${label}`);
  if(control.checked!==String(checked)) {tap(control);await pause(200);}
}
async function openSettings() {await tapText('Settings');}
function startApp() {shell('am','start','-S','-W','-n','com.openticker.app/.MainActivity');}

try {
  assert.equal(shell('getprop','ro.kernel.qemu'),'1','Only emulator test devices are supported');
  assert.equal(shell('getprop','sys.boot_completed'),'1','Wait for the emulator to boot');
  adb('install','-r',process.env.PLAYBACK_TEST_APK ?? 'app/build/outputs/apk/debug/app-debug.apk');
  assert.equal(shell('pm','clear','com.openticker.app'),'Success','Reset only the disposable emulator app for fresh-install checks');
  adb('logcat','-c');
  shell('wm','size','1280x800');shell('wm','density','160');
  shell('settings','put','system','accelerometer_rotation','0');
  shell('settings','put','secure','immersive_mode_confirmations','confirmed');
  for(const key of ['window_animation_scale','transition_animation_scale','animator_duration_scale']) shell('settings','put','global',key,'0');
  // Keep UI verification independent of live weather/sports/market APIs.
  shell('svc','wifi','disable');shell('svc','data','disable');
  startApp();await pause(1500);
  const welcome=nodes().find(n=>n.text?.toLowerCase()==='got it');if(welcome) tap(welcome);
  if(nodes().some(n=>n.text==='Pause')) await tapText('Pause');
  await openSettings();
  let defaults=nodes();
  for(let attempt=0;!defaults.some(n=>n.text==='15s') && attempt<5;attempt++) {
    shell('input','swipe','640','610','640','320','250');defaults=nodes();
  }
  assert.ok(defaults.some(n=>n.text==='15s' && isSelected(defaults,n)),'Fresh installs default to 15 seconds');
  await tapText('Cancel');await switchTo('Weather');
  const cycleStarted=performance.now();await tapText('Resume');
  for(const [label,deadline] of [['Sports',15],['Stocks',30],['Weather',45]]) {
    while(true) {
      const tree=nodes(), elapsed=performance.now()-cycleStarted;
      if(screenPresent(tree,label)) {
        assert.ok(elapsed>=(deadline-1)*1000,`${label} appeared before its 15-second dwell deadline`);
        report.defaultCycle.push({screen:label,observedAtMs:Math.round(elapsed)});break;
      }
      assert.ok(elapsed<(deadline+10)*1000,`Default cycle did not reach ${label}`);
      await pause(150);
    }
  }
  await tapText('Pause');console.log('PASS default Weather / Sports / Stocks cycle at 15 seconds per screen');
  for(const label of ['Stocks','Weather','Sports','Weather','Stocks','Sports']) {
    await switchTo(label);report.manualScreens.push(label);
    if(label==='Stocks') assert.ok(nodes().some(n=>n.text.startsWith('Stock data not configured')),'Unconfigured stocks display clear setup guidance');
    screenshot(label.toLowerCase());
    console.log(`PASS manual ${label} button`);
  }
  for(const seconds of [10,15,20]) {
    await openSettings();await tapText(`${seconds}s`,{scroll:true});
    await tapText('Cancel');
    // A chip tap must survive both Cancel and a process restart, without a full settings save.
    shell('am','force-stop','com.openticker.app');startApp();await pause(500);
    await openSettings();
    let tree=nodes();
    for(let attempt=0;!tree.some(n=>n.text===`${seconds}s`) && attempt<5;attempt++) {
      shell('input','swipe','640','610','640','320','250');tree=nodes();
    }
    assert.ok(tree.some(n=>n.text===`${seconds}s` && isSelected(tree,n)),`${seconds}s selection was not persisted`);
    screenshot(`settings-${seconds}s`);await tapText('Cancel');
    await switchTo('Weather');
    // Resume after manual selection: the chosen screen must get the full configured dwell.
    const resume=nodes().find(n=>n.text==='Resume');assert.ok(resume,'Playback remains paused while choosing an interval');
    const started=performance.now();tap(resume);
    await pause(seconds*500);
    assert.ok(screenPresent(nodes(),'Weather'),`${seconds}s interval switched too early`);
    let switched=false,elapsed=0;
    while(performance.now()-started<(seconds+10)*1000) {
      if(screenPresent(nodes(),'Sports')) {elapsed=performance.now()-started;switched=true;break;}
      await pause(150);
    }
    assert.ok(switched,`${seconds}s interval did not advance to Sports`);
    assert.ok(elapsed>=(seconds-1)*1000,`${seconds}s interval advanced early`);
    report.intervals.push({seconds,observedSwitchMs:Math.round(elapsed),persistedAfterRestart:true});
    console.log(`PASS ${seconds}s interval, observed Sports at ${(elapsed/1000).toFixed(1)}s`);
    await tapText('Pause');
  }
  await openSettings();
  for(const label of ['Weather','Sports','Stocks']) await setToggle(label,false);
  await tapText('Save settings');
  for(const label of ['Weather','Sports','Stocks']) {
    await switchTo(label);report.disabledScreens.push(label);
    console.log(`PASS manual ${label} with automatic screens disabled`);
  }
  // Restore defaults for the ordinary screenshot/video capture that follows in CI.
  await openSettings();
  for(const label of ['Weather','Sports','Stocks']) await setToggle(label,true);
  await setToggle('Pause automatic switching',false);
  await tapText('15s',{scroll:true});await tapText('Save settings');
  await switchTo('Weather');
  const logs=adb('logcat','-d','-v','threadtime').toString();
  assert.ok(!logs.includes('FATAL EXCEPTION'),'No Android runtime crashes during playback checks');
  report.passed=true;
} catch(error) {
  report.passed=false;report.error=String(error);
  try {screenshot('failure');} catch {}
  throw error;
} finally {
  writeFileSync(join(output,'results.json'),JSON.stringify(report,null,2));
  try {
    writeFileSync(join(output,'runtime.log'),adb('logcat','-d','-v','threadtime'));
    shell('svc','wifi','enable');shell('svc','data','enable');
    for(const key of ['window_animation_scale','transition_animation_scale','animator_duration_scale']) shell('settings','put','global',key,'1');
  } catch {}
}
