# Tobacco & Bordeaux · Light / Airy — token table (source of truth for TH1)

Source: `robia-palette-studio.html`, entry id `tobacco-bordeaux-airy` (planner copy: `~/.local/share/robia-planning/r5/tobacco-bordeaux-airy.json`).
One planner correction (R5-DEC-D03): light `onTertiaryContainer` `#8E4E58` → **`#FFD9DE`** (was 1.61:1 on `#6A303A`, now 7.74:1). Light `sparkOnPrimary` follows it (today's rule: spark = light onTertiaryContainer).

## Material roles

| Role | Light | Dark |
|---|---|---|
| primary | `#5A3B2D` | `#F2D6C8` |
| onPrimary | `#FBF7F1` | `#46291C` |
| primaryContainer | `#76584A` | `#5A3B2D` |
| onPrimaryContainer | `#F7EDE6` | `#D1A693` |
| inversePrimary | `#EABDAA` | `#795647` |
| secondary | `#73594D` | `#E1BFB1` |
| onSecondary | `#FFFFFF` | `#412B22` |
| secondaryContainer | `#F3E4DA` | `#6B4C4A` |
| onSecondaryContainer | `#2A1B15` | `#F5EBDD` |
| tertiary | `#4E1B25` | `#FFB2BC` |
| onTertiary | `#FFFFFF` | `#531F29` |
| tertiaryContainer | `#6A303A` | `#6A303A` |
| onTertiaryContainer | `#FFD9DE` (fixed) | `#E79AA4` |
| background | `#FCFAF8` | `#211715` |
| onBackground | `#221814` | `#EBE0DD` |
| surface | `#FCFAF8` | `#211715` |
| onSurface | `#221814` | `#EBE0DD` |
| surfaceVariant | `#E3D9D0` | `#504440` |
| onSurfaceVariant | `#5C4A43` | `#D4C3BD` |
| surfaceTint | `#795647` | `#EABDAA` |
| inverseSurface | `#352F2D` | `#EBE0DD` |
| inverseOnSurface | `#F9EEEB` | `#352F2D` |
| error | `#BA1A1A` | `#FFB4AB` |
| onError | `#FFFFFF` | `#690004` |
| errorContainer | `#FFDAD5` | `#930009` |
| onErrorContainer | `#930009` | `#FFDAD5` |
| outline | `#82746F` | `#9C8E88` |
| outlineVariant | `#D4C3BD` | `#504440` |
| scrim | `#000000` | `#000000` |
| surfaceBright | `#FCFAF8` | `#3E3836` |
| surfaceDim | `#E2D8D4` | `#171211` |
| surfaceContainer | `#F3ECE6` | `#3D2C27` |
| surfaceContainerHigh | `#EBE3DC` | `#2E2927` |
| surfaceContainerHighest | `#E3D9D0` | `#393431` |
| surfaceContainerLow | `#F8F3EF` | `#1F1B19` |
| surfaceContainerLowest | `#FFFFFF` | `#110D0C` |

## RobiaExtras

| Token | Light | Dark |
|---|---|---|
| AddBorder | `#9CC8AA` | `#4F8F66` |
| AddContainer | `#DCEFE1` | `#1D4430` |
| AddSign | `#2E6B47` | `#8FD3A5` |
| OnAddContainer | `#17432A` | `#C6F2D3` |
| OnAddSign | `#FFFFFF` | `#0B2615` |
| OnRemoveContainer | `#93000A` | `#FFDAD6` |
| OnRemoveSign | `#FFFFFF` | `#5C0A05` |
| RemoveBorder | `#EAA59C` | `#B5554B` |
| RemoveContainer | `#FFDAD6` | `#5E1F19` |
| RemoveSign | `#BA1A1A` | `#FFB4AB` |
| SparkOnPrimary | `#FFD9DE` | `#6A303A` |
| PhotoWellTop / PhotoWellBottom | `#F3F3F4` / `#D1CFCF` | same (unchanged from Earth & Steel) |

Add/remove chip colours and photo wells are identical to Earth & Steel.

## Contrast of text pairs (WCAG, needs ≥ 4.5)

| Pair | Light | Dark |
|---|---|---|
| onSurface / surface | 16.69 | 13.55 |
| onSurfaceVariant / surface | 8.02 | 10.30 |
| onSurfaceVariant / surfaceContainer | 7.14 | 7.77 |
| onPrimary / primary | 9.38 | 9.55 |
| onPrimaryContainer / primaryContainer | 5.58 | 4.57 |
| onSecondary / secondary | 6.43 | 7.71 |
| onSecondaryContainer / secondaryContainer | 13.36 | 6.46 |
| onTertiary / tertiary | 13.93 | 7.74 |
| onTertiaryContainer / tertiaryContainer | 7.74 | 4.56 |
| onError / error | 6.46 | 7.72 |
| onErrorContainer / errorContainer | 7.24 | 7.24 |
| inverseOnSurface / inverseSurface | 11.57 | 10.18 |
| sparkOnPrimary / primary | 7.74 | 7.26 |

Outline / surface (edges, needs ≥ 3): light 4.31, dark 5.54.
