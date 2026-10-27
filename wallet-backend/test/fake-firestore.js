'use strict';
const assert=require('node:assert/strict');
class Timestamp{constructor(ms){this.ms=ms;}toMillis(){return this.ms;}static fromMillis(ms){return new Timestamp(ms);}}
const clone=v=>v instanceof Timestamp?new Timestamp(v.ms):Array.isArray(v)?v.map(clone):v&&typeof v==='object'?Object.fromEntries(Object.entries(v).map(([k,x])=>[k,clone(x)])):v;
class FakeFirestore{
  constructor(){this.docs=new Map();this.queue=Promise.resolve();}
  snapshot(ref){const d=this.docs.get(ref.path);return {id:ref.id,ref,exists:d!==undefined,data:()=>clone(d),get:k=>clone(d?.[k])};}
  doc(path){const ref={path,id:path.split('/').pop()};ref.get=async()=>this.snapshot(ref);return ref;}
  collection(name){return this.query(name);}
  query(name,filters=[],sort=null,max=Infinity,cursor=null){
    const next=(f=filters,s=sort,m=max,c=cursor)=>this.query(name,f,s,m,c);
    return {where:(k,op,v)=>next([...filters,[k,op,v]]),orderBy:(k,d='asc')=>next(filters,[k,d]),limit:n=>next(filters,sort,n),startAfter:doc=>next(filters,sort,max,doc),get:async()=>{
      let docs=[...this.docs.keys()].filter(k=>k.startsWith(name+'/')&&k.split('/').length===name.split('/').length+1).map(k=>this.snapshot(this.doc(k)));
      for(const[k,op,v]of filters){assert.equal(op,'==');docs=docs.filter(d=>d.get(k)===v);}
      if(sort){const[k,d]=sort;const val=x=>k==='__name__'?x.id:(x.get(k)?.toMillis?.()??x.get(k));docs.sort((a,b)=>(val(a)<val(b)?-1:val(a)>val(b)?1:0)*(d==='desc'?-1:1));}
      if(cursor){const at=docs.findIndex(d=>d.id===(typeof cursor==='string'?cursor:cursor.id));docs=docs.slice(at+1);}return {docs:docs.slice(0,max),empty:docs.length===0,size:Math.min(docs.length,max)};
    }};
  }
  runTransaction(fn){const run=this.queue.then(async()=>{
    const writes=[];const tx={get:async ref=>{assert.equal(writes.length,0,'Firestore transaction read after write');return ref.path?this.snapshot(ref):ref.get();},create:(r,d)=>writes.push(['create',r,d]),set:(r,d)=>writes.push(['set',r,d]),update:(r,d)=>writes.push(['update',r,d]),delete:r=>writes.push(['delete',r])};
    const result=await fn(tx);const next=new Map([...this.docs.entries()].map(([k,v])=>[k,clone(v)]));
    for(const[op,r,d]of writes){if(op==='delete'){next.delete(r.path);continue;}if(op==='create')assert(!next.has(r.path),'Duplicate create '+r.path);if(op==='update')assert(next.has(r.path),'Update missing');next.set(r.path,op==='update'?{...next.get(r.path),...clone(d)}:clone(d));}
    this.docs=next;return clone(result);
  });this.queue=run.catch(()=>{});return run;}
}
module.exports={FakeFirestore,Timestamp};
