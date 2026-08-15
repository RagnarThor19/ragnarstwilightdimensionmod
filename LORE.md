# The Twilight — lore bible

A working document. Nothing here is fixed; change anything that stops being useful.

Two tags are used throughout:

- **[built]** — this is in the mod right now, and the file it lives in is named. Changing it means changing code or data.
- **[idea]** — not built. A proposal, kept here so it is not lost.

The one rule the document itself follows: **if it needs a paragraph of text in-game to land, it is not
finished.** Everything below is meant to be delivered through terrain, loot, behaviour, sound and
numbers.

The entire text budget of the mod has already been spent, on three words in a book nobody signed. See
§3. Anything proposed from here has to work without words.

---

## 1. The premise

The Twilight is what is between the Overworld and the End, and you were never supposed to stop in it.

An End portal takes twelve frames and twelve eyes. The gateway into the Twilight is **one** frame and
**one** eye. You paid a twelfth of the fare and you got a twelfth of the way, and then the journey
ended and left you standing in the part nobody was meant to see — a place assembled out of remembered
Overworld, at the wrong scale, with the sky missing.

It is not a hell and it is not a test. Nothing here wants anything from you. It is a waiting room that
has been running long enough that the people in it have stopped being people.

The question the player should never quite resolve is not *"is this dangerous"* — it obviously is —
but ***"is any of this actually here"***. Every mechanic below should be answerable either way.

---

## 2. Rules of the place

Constraints. Breaking one of these costs more than whatever it buys.

1. **Nothing here is raw.** No ore, no water, no weather, no animals. Nothing grows and nothing
   renews. The only renewable resource in the dimension is graves. **[built]** — see
   `worldgen/noise_settings/twilight.json` (`ore_veins_enabled: false`, `aquifers_enabled: false`) and
   `worldgen/biome/twilight_plains.json` (`has_precipitation: false`, every spawner list empty).
2. **The place never confirms anything.** Compasses and clocks spin — the dimension is
   `natural: false`. **[built]** The one time it does claim something, it lies: `fixed_time: 6000` is
   *noon*, and it does not look like noon. **[built]**
3. **Nothing can be verified up close.** The silhouettes refuse to be approached, the wanderer never
   acknowledges you, the leviathan never appears. Anything the player can walk up to and inspect at
   leisure stops being evidence and starts being a mob. **[built]**
4. **Nothing is explained by anything that can talk.** There are no NPCs, no books that tell the
   story, no advancement text. There is nobody left who could have written it down.
5. **The fog is the honest part.** Eleven blocks. Everything else about the dimension is a copy of
   somewhere else; the fog is the actual size of the room. **[built]** — `TwilightFog`

---

## 3. What is already canon

The strongest lore in the mod was written by accident, in data files. It is canon because it is
already shipping.

### The fare is one eye, and it is consumed

`TwilightPortal` turns a single eyeless End portal frame into a two-way gateway. An eye of ender is
destroyed on each crossing. **[built]**

**Meaning:** every trip in and every trip out costs one eye. A player who arrives without a spare is
staying.

### The graves are full of people who were doing what you are doing

`loot_table/chests/gravestone.json` rolls the **stronghold library** table, plus a 45% chance of 1–2
ender eyes. **[built]**

**Meaning:** the dead here were stronghold-bound, carrying the same currency, on the same errand. And
since the way home costs an eye, **the way home is to rob the ones who did not make it.** This is the
moral premise of the dimension and it is already implemented, in one JSON file, with zero words.

### Somebody lived in the abandoned house, and they never spent their last eye

`loot_table/chests/abandoned_building.json` is a household, not a treasure chest: food, worn tools and
armour that stop at iron, timber, seeds, a few mob drops — and **exactly one eye of ender**,
guaranteed, every time. **[built]**

**Meaning:** one eye is the exact fare out. Whoever lived here had the price of the crossing in a box
by the bed for as long as they were here, and never used it. They built a house instead.

The chest is also the one container in the game whose contents are laid out in a **tidy row**, filled
from the left, instead of scattered across the slots the way every generated chest in Minecraft is.
**[built]** — `LootableInventoryMixin`. Nothing else in the dimension is tidy. Somebody packed this.

### The only writing in the world

A third of those chests end the row with a **book and quill**, unsigned, one page, three words:

> why 64?

**[built]**

This is the entire text budget of the mod and it should stay that way. It works because of what it is
not: not a journal, not an explanation, not a warning, and not addressed to anyone. Somebody who lived
here noticed the number, wrote it down, and never got further than the question. It is the only
evidence in the dimension that anyone here was *thinking*.

It is also a load-bearing hint: it tells the player the number matters without telling them what it
counts, which is the only push they need to start counting things. See §5.

Rules for it, in descending order of importance:

1. **Never answer it.** Not in another book, not in an advancement, not anywhere.
2. **Never add a second book.** One question, in one place, found by chance. Two books is a
   correspondence, and a correspondence has an author.
3. Leave it unsigned. A signed book has a name on it, and a name is a person, and there are no people.

### Somebody got here first

Rarely — four times rarer than an ordinary grave — a grave generates that has **already been dug
out**. **[built]** — `GravestoneFeature.Kind.OPENED`

It is the same grave, built by the same code, off the same ground checks. The headstone is there, the
blank sign is there, the coarse dirt at the foot is there. What is missing is the mound over the
chest, the chest, and the figure that stands at every other one.

**Meaning:** nothing is stated, and nothing should be. The scene is only legible to someone who has
already found several intact ones, which is what the rarity is for — the first grave a player sees
must never be an open one. What it says, to a player who has been prising the lids off the others, is
that they are not the first person to do this. And that the watcher who stands over a full grave does
not stay once it is empty.

### The ground is at somebody else's sea level

The terrain density gradient runs y58 → y74, so the surface sits around **y64** — the Overworld's
shoreline. The dimension's own declared `sea_level` is **-64**, at the very bottom of the world, and
it is dry. **[built]** — `worldgen/noise_settings/twilight.json`

**Meaning:** the place kept the number and lost what it was for. It is built at the height something
else's ground was.

### The sky is not dim, it is absent

The dimension type borrows `minecraft:the_nether` effects, whose sky type is NONE. Nothing is drawn up
there at all; what looks like a dusk sky is the clear colour of an empty frame. **[built]**

**Meaning:** the void of the End, painted the colour of an Overworld evening. During a blood moon the
paint slips — the fog goes red while the background does not, and the whole landscape comes through as
hard silhouettes out to the horizon. That bug-that-is-not-a-bug is the mask coming off, and the
comment in `BackgroundRendererMixin` exists to stop anyone "fixing" it. **[built]**

### You can lie down but you cannot wake up

`bed_works: true`, with `fixed_time`. **[built]**

**Meaning:** you can sleep here and set your spawn here, and the night will never pass. Choosing to
respawn in the Twilight is the only irreversible decision available in the dimension.

---

## 4. Who the Steves are

**They are all one person, at different distances from having arrived.**

Size is not power. Size is **tenure**. The longer something has been here, the larger it is and the
less it moves. The existing entities already form the ladder, in this order:

| | height | what it does | what it is |
|---|---|---|---|
| **Blood steve** | 1.8 | sprints straight at you, 1 HP, dies to anything | just arrived — a touch undoes them |
| **Silhouette** | 1.8 | watches from the fog line, leaves if approached | here long enough to have learned not to be verified |
| **Wanderer** | 4.0 | crosses in a straight line, never acknowledges you, more footfalls than legs | has somewhere to be, and is no longer only bipedal |
| **Giant** | 20.0 | never moves. Turns to face you. That is all | has stopped going anywhere |
| **Leviathan** | — | never seen. Heard at 512 blocks | past having a shape |

All **[built]**.

### The giant

He is not a boss, a god, or a warning. **He is the oldest one.** He does not attack because he does
not need to — he is not the threat, he is the outcome. He stands seventy-five blocks out, inside the
fog, so he is never anything but an outline; walking towards him does not resolve him into anything,
because he turns to keep facing you the whole way in.

The blood moon is the only time the whole ladder is visible at once: sixty-four of the newest ones
running at you while the oldest one watches from outside the fog and does nothing.

### Why they are all Steve

Because the dimension has one template. It knows a player arrived; it does not know **which**. If the
player wears a custom skin, every copy of them here is wearing the default — the copy is wrong, and
the player can see exactly how wrong, every time, without being told.

- **[idea]** One entity, once, late in the game, rendered in the player's *own* skin. Needs the
  client's skin texture in the renderer, so it is real work. It is the entire story in one frame and
  should be spent carefully — probably once per world, and never during a blood moon.

### Where the player is on the ladder

At the bottom. 1.8 blocks, one arrival, freshly here. The blood moon is what it looks like when the
place tries to file you.

---

## 5. The number 64

**One meaning, repeated.** Scattering the number everywhere makes it decoration; using it for one
thing makes it a fact the player can find.

The meaning: **the Twilight is the Overworld folded 64 to 1.** The dimension's coordinate scale is 8,
so one block of ground here is 8×8 = 64 blocks of ground there. The mod's own `TwilightPortal`
javadoc already says "sixty-four times denser". **[built]**

That is why the place is made of second-hand Overworld things, and why it can only afford one template
for a person.

Where it recurs:

- **The blood moon runs 64 seconds** (1280 ticks) — the length of the track. **[built]**
- **Exactly 64 arrive**, one every 0.8s, the last landing at 63.2s so the pressure runs right up to
  the final note. **[built]** — `BloodMoon.SPAWNS_PER_EVENT`. The interval is derived from the count,
  not the other way round, so retuning the duration cannot silently break it.
- **[idea]** Blood moon cooldown of 64 minutes rather than 30.
- **[idea]** Exactly 64 gravestones in the world, and no more ever generated.

Where it deliberately does **not** recur:

- The giant stands at **75** blocks, not 64. One thing should refuse to fit the pattern, and it should
  be the thing at the top of the ladder. **[built]**

---

## 6. Beats worth building

All **[idea]** unless noted.

### The unfinished portal room

Make the twilight ruin read as a stronghold portal room that was never completed: the twelve-socket
ring scratched into the floor, and exactly **one** frame actually placed in it. Someone came out here
and tried to build the End, and got one twelfth of the way.

The same distance you got.

### Endstone underneath

The surface keeps pretending to be the Overworld. Below some depth the stone becomes endstone. The
disguise is skin-deep, and digging is what finds out. Makes "stopped halfway" vertical and literal.

### Numbered signs

If two characters of text are affordable: number the graves. The highest number a player can ever find
is **63**.

### The bed consequence

The first time a player sets their spawn point in the Twilight, a fresh gravestone generates near the
bed. The world acknowledges the decision and never mentions it again.

### Buildings that do not work

`abandoned_building` and the ruins should never generate *complete*, and never with an exterior door.
Rooms that were remembered from the inside.

---

## 7. Never answer

- **Why 64.** The book asks it. Nothing in the mod should ever reply. The player can *find* the
  answer — by counting the arrivals, by noticing the coordinate scale, by standing at y64 — but the
  world must never confirm they got it right.
- **What the leviathan is.** It never appears. It is load-bearing precisely because it is the rung
  past the giant — the giant only reads as "the far end" while there is something further out that can
  be heard and never reached.
- **Who is buried in the graves.** The loot table already says everything necessary about them.
- **What is on the other side of the crossing you did not complete.** The End is a real place the
  player can go by other means. Nothing here should confirm that it is the same End.

---

## 8. Open questions

- Does the player's own gravestone exist somewhere, already dug and already filled? Powerful once;
  unrecoverable if it lands wrong.
- Should the wanderer be going *somewhere specific* — the same bearing every time, world-wide — so a
  player who tracks several of them finds something at the convergence? Currently its bearing is
  random.
- Is there anything at all at y-64, where the dimension says its sea is?
