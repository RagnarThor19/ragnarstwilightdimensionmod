// Bakes the seated figure into a church template as a structure entity, so worldgen places him with
// the building. See README.md.
//
//   node bake.js <source .nbt> <destination .nbt>
//
// Source and destination may be the same file. The entities list is replaced wholesale; every other
// tag is passed through byte for byte.
const nbt=require('./nbtio.js');
const SRC=process.argv[2], DST=process.argv[3];
if(!SRC||!DST){console.error('usage: node bake.js <source .nbt> <destination .nbt>');process.exit(1)}

const ID='ragnarstwilightdimension:church_steve';
const BLOCK=[13,1,8];              // the pew stair he is sitting on
const POS=[13.75,1.5,8.5];         // clear of the backrest, on the seat surface
const YAW=-90.0, PITCH=0.0;        // due east, along the pew, at the altar end

const D=v=>({t:6,v}), F=v=>({t:5,v}), I=v=>({t:3,v}), S=v=>({t:8,v});
const list=(et,items)=>({t:9,et,v:items});
const comp=pairs=>({t:10,v:pairs});

const entity=comp([
 ['blockPos',list(3,BLOCK.map(I))],
 ['pos',list(6,POS.map(D))],
 ['nbt',comp([
   ['id',S(ID)],
   ['Pos',list(6,POS.map(D))],
   ['Motion',list(6,[0,0,0].map(D))],
   ['Rotation',list(5,[YAW,PITCH].map(F))],
 ])],
]);

const {rootName,root}=nbt.read(SRC);
const entities=nbt.get(root,'entities');
entities.et=10;
entities.v=[entity];
nbt.write(DST,rootName,root);

// read it back and report
const back=nbt.read(DST);
const e=nbt.get(back.root,'entities');
console.log('entities now',e.v.length,'element type',e.et);
console.log(JSON.stringify(e.v[0],(k,v)=>typeof v==='bigint'?v.toString():v));
console.log('size',nbt.get(back.root,'size').v.map(x=>x.v).join('x'),'blocks',nbt.get(back.root,'blocks').v.length);
