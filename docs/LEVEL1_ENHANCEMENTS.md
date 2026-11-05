# Level 1 location enhancement

Replaced the three repeated pillar/crystal compositions with:

* Spring Garden: four planted beds, stepping stones, a rear bench and rose trellis,
  with a fenced perimeter and open southern entrance.
* Overlook Cottage: slate roof, stone courses, chimney, shuttered windows, flower
  boxes, front steps, lantern, climbing vine, barrel and side flower garden.
* Bellwarden Farmstead: warm tiled roof, timber wall texture, vegetable beds,
  barrels and an overgrown facade.

Terrain recipes, roads, bridges, forest clusters, spawn and gates retain their
previous coordinates. Level 2 and Level 3 are unchanged. New walls and garden
fences have collision; flowers and planting beds remain traversable. Placement is
deterministic. The artwork is cached with transparency and uses existing depth
passes rather than new per-frame asset generation.

Verification: Java 17 build, full WorldIntegrationTest and diff whitespace check
pass. All landmark approaches and gates remain reachable. LocationPreview renders
the actual Swing GamePanel and verifies each new entrance is walkable. Captures
are under `out/location-previews/`: garden.png, cottage.png, farmhouse.png.
These are game-render captures, not desktop screenshots.
