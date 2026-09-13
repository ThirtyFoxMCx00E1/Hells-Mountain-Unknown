# app/src/main/game

Gameplay code lives here going forward - separate from `cpp/` (native
renderer/audio engine) and from the launcher UI classes in
`java/com/unsolvedcase/game/`.

Box2D was removed (2D physics, never actually wired into anything, and
the game is 3D). Once real gameplay mechanics start (investigation
interactions, puzzles, movement), they go here rather than in the
native renderer files, to keep "engine plumbing" and "actual game
logic" separate as the project grows.
