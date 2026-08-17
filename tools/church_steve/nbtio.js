// Lossless typed NBT read/write. Tree nodes: {t, v} (+ et for lists).
const fs=require('fs'),zlib=require('zlib');

function read(file){
 let buf=fs.readFileSync(file);
 if(buf[0]===0x1f&&buf[1]===0x8b)buf=zlib.gunzipSync(buf);
 let p=0;
 const str=()=>{const l=buf.readUInt16BE(p);p+=2;const s=buf.toString('utf8',p,p+l);p+=l;return s};
 function payload(t){
  switch(t){
   case 1:{const v=buf.readInt8(p);p+=1;return {t,v}}
   case 2:{const v=buf.readInt16BE(p);p+=2;return {t,v}}
   case 3:{const v=buf.readInt32BE(p);p+=4;return {t,v}}
   case 4:{const v=buf.readBigInt64BE(p);p+=8;return {t,v}}
   case 5:{const v=buf.readFloatBE(p);p+=4;return {t,v}}
   case 6:{const v=buf.readDoubleBE(p);p+=8;return {t,v}}
   case 7:{const n=buf.readInt32BE(p);p+=4;const a=[];for(let i=0;i<n;i++){a.push(buf.readInt8(p));p+=1}return {t,v:a}}
   case 8:return {t,v:str()};
   case 9:{const et=buf.readUInt8(p);p+=1;const n=buf.readInt32BE(p);p+=4;const a=[];for(let i=0;i<n;i++)a.push(payload(et));return {t,et,v:a}}
   case 10:{const a=[];for(;;){const t2=buf.readUInt8(p);p+=1;if(t2===0)break;const nm=str();a.push([nm,payload(t2)])}return {t,v:a}}
   case 11:{const n=buf.readInt32BE(p);p+=4;const a=[];for(let i=0;i<n;i++){a.push(buf.readInt32BE(p));p+=4}return {t,v:a}}
   case 12:{const n=buf.readInt32BE(p);p+=4;const a=[];for(let i=0;i<n;i++){a.push(buf.readBigInt64BE(p));p+=8}return {t,v:a}}
   default:throw new Error('bad tag '+t+' @'+p);
  }
 }
 const rt=buf.readUInt8(p);p+=1;const rootName=str();
 return {rootName,root:payload(rt)};
}

function write(file,rootName,root){
 const parts=[];
 const push=b=>parts.push(b);
 const num=(n,fn,len)=>{const b=Buffer.alloc(len);b[fn](n,0);push(b)};
 const str=s=>{const b=Buffer.from(s,'utf8');const h=Buffer.alloc(2);h.writeUInt16BE(b.length,0);push(h);push(b)};
 function payload(node){
  switch(node.t){
   case 1:num(node.v,'writeInt8',1);break;
   case 2:num(node.v,'writeInt16BE',2);break;
   case 3:num(node.v,'writeInt32BE',4);break;
   case 4:num(BigInt(node.v),'writeBigInt64BE',8);break;
   case 5:num(node.v,'writeFloatBE',4);break;
   case 6:num(node.v,'writeDoubleBE',8);break;
   case 7:{num(node.v.length,'writeInt32BE',4);const b=Buffer.alloc(node.v.length);node.v.forEach((x,i)=>b.writeInt8(x,i));push(b);break}
   case 8:str(node.v);break;
   case 9:{const et=node.v.length?node.v[0].t:(node.et||0);num(et,'writeUInt8',1);num(node.v.length,'writeInt32BE',4);node.v.forEach(payload);break}
   case 10:{for(const [nm,child] of node.v){num(child.t,'writeUInt8',1);str(nm);payload(child)}num(0,'writeUInt8',1);break}
   case 11:{num(node.v.length,'writeInt32BE',4);const b=Buffer.alloc(node.v.length*4);node.v.forEach((x,i)=>b.writeInt32BE(x,i*4));push(b);break}
   case 12:{num(node.v.length,'writeInt32BE',4);const b=Buffer.alloc(node.v.length*8);node.v.forEach((x,i)=>b.writeBigInt64BE(BigInt(x),i*8));push(b);break}
   default:throw new Error('bad tag '+node.t);
  }
 }
 num(root.t,'writeUInt8',1);str(rootName);payload(root);
 fs.writeFileSync(file,zlib.gzipSync(Buffer.concat(parts)));
}

const get=(comp,name)=>{const e=comp.v.find(([n])=>n===name);return e&&e[1]};
const set=(comp,name,node)=>{const e=comp.v.find(([n])=>n===name);if(e)e[1]=node;else comp.v.push([name,node])};
module.exports={read,write,get,set};
