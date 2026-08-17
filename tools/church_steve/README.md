# Putting the churchgoer back

The seated figure in the church is not spawned by code. He is baked into
`src/main/resources/data/ragnarstwilightdimension/structure/twilight_church.nbt` as a structure
entity, so worldgen places him with the building and places him exactly once - which is what makes
killing him permanent.

The cost is that he lives in the template. **Re-exporting the church from a creative world overwrites
that file and drops him.** When that happens:

    node tools/church_steve/bake.js <the newly exported .nbt> src/main/resources/data/ragnarstwilightdimension/structure/twilight_church.nbt

It reads the template, replaces the `entities` list with the one figure, and writes it back out
gzipped. Everything else in the file is passed through untouched - the round trip is byte-exact on a
file it has not been asked to change.

If the pews move, change `BLOCK` and `POS` at the top of `bake.js`, and change `HEAD_TURN` in
`ChurchSteveEntity` to match the new bearing from the seat to the lectern.
