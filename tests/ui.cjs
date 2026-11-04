const fs=require('fs'),path=require('path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.resolve(__dirname,'..'),data=JSON.parse(fs.readFileSync(path.join(root,'data/status.json'),'utf8'));
(async()=>{
 const out=process.env.QA_OUTPUT||path.join(root,'qa-output');fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({headless:true,...(process.env.EDGE_TEST?{channel:'msedge'}:{})});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto('file:///'+path.join(root,'app/src/main/assets/index.html').replaceAll('\\','/'));
  assert.equal(await page.evaluate(d=>window.Governor.receive(d,'online'),data),true);
  assert.equal(await page.locator('#delivered').innerText(),'0 / '+data.tasks.length);
  await page.locator('#nav-tasks').click();
  assert.equal(await page.locator('#all-tasks details').count(),data.tasks.length);
  await page.locator('[data-owner="map"]').click();
  assert.equal(await page.locator('#all-tasks details').count(),data.tasks.filter(t=>/Mapchat|Beide chats/.test(t.owner)).length);
  await page.locator('[data-owner="all"]').click();await page.locator('#all-tasks [data-task="mirrors"] summary').click();
  assert.equal(await page.locator('#all-tasks [data-task="mirrors"] .checks li').count(),9);
  await page.locator('#nav-history').click();assert.ok(await page.locator('.history-entry').count()>0);
  const invalid=structuredClone(data);invalid.tasks[0].status='delivered';
  assert.equal(await page.evaluate(d=>window.Governor.receive(d,'online'),invalid),false);
  assert.equal(await page.locator('#delivered').innerText(),'0 / '+data.tasks.length);
  const old=structuredClone(data);old.updatedAt='2000-01-01T00:00:00+00:00';
  assert.equal(await page.evaluate(d=>window.Governor.receive(d,'online'),old),false);
  const hostile=structuredClone(data);hostile.tasks[0].title='<img src=x onerror="window.hacked=true">';
  assert.equal(await page.evaluate(d=>window.Governor.receive(d,'online'),hostile),true);
  assert.equal(await page.evaluate(()=>window.hacked),undefined);
  assert.equal(await page.locator('.task img').count(),0);
  await page.evaluate(d=>window.Governor.receive(d,'cached'),data);
  await page.evaluate(()=>window.Governor.connection('error'));
  assert.match(await page.locator('#connection-label').innerText(),/vorige stand/);
  await page.locator('#nav-overview').click();
  for(const width of [320,390,720]){
   await page.setViewportSize({width,height:844});
   await page.evaluate(()=>new Promise(r=>requestAnimationFrame(r)));
   const size=await page.evaluate(()=>({client:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth}));
   assert.ok(size.scroll<=size.client,JSON.stringify(size));
   await page.screenshot({path:path.join(out,'overview-'+width+'.png')});
  }
  await page.setViewportSize({width:390,height:844});await page.locator('#nav-tasks').click();
  await page.screenshot({path:path.join(out,'tasks-390.png')});
  assert.deepEqual(errors,[]);console.log(JSON.stringify({passed:true,tasks:data.tasks.length,viewports:[320,390,720],checks:['navigation','owner filter','checklist','history','invalid update','older update','text injection','offline message']}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
