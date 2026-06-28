---
name: Earth & Steel
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f4'
  surface-container: '#efe5d9'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1a1c1c'
  on-surface-variant: '#4f4442'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f0f1f1'
  outline: '#817472'
  outline-variant: '#d2c3c0'
  surface-tint: '#6d5a56'
  primary: '#1d100d'
  on-primary: '#ffffff'
  primary-container: '#332421'
  on-primary-container: '#a08a85'
  inverse-primary: '#dac1bc'
  secondary: '#6b5c4d'
  on-secondary: '#ffffff'
  secondary-container: '#f1dcc9'
  on-secondary-container: '#6f6051'
  tertiary: '#240d00'
  on-tertiary: '#ffffff'
  tertiary-container: '#431d00'
  on-tertiary-container: '#c37f50'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#f7ddd8'
  primary-fixed-dim: '#dac1bc'
  on-primary-fixed: '#261815'
  on-primary-fixed-variant: '#54433f'
  secondary-fixed: '#f4dfcc'
  secondary-fixed-dim: '#d7c3b0'
  on-secondary-fixed: '#241a0e'
  on-secondary-fixed-variant: '#524436'
  tertiary-fixed: '#ffdcc7'
  tertiary-fixed-dim: '#ffb786'
  on-tertiary-fixed: '#311300'
  on-tertiary-fixed-variant: '#6d390f'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  margin: 24px
  gutter: 16px
---

# Design System: Earth & Steel

## Brand & Style
Earth & Steel is a brand that balances rugged, organic warmth with professional, architectural precision. The identity has shifted toward a more softened, sophisticated direction by introducing a muted, champagne-toned secondary color while maintaining its grounded foundation. It evokes a sense of reliability, craftsmanship, and tactile quality.

The design style is **Corporate / Modern** with a focus on **Minimalism**. It utilizes generous whitespace, high-quality typography, and a refined color palette to create a premium feel. The interface feels structured and deliberate, combining architectural stability with subtle, elegant focal points.

## Colors
The color palette is anchored by deep, earthy tones contrasted with soft, sophisticated neutrals. The primary color is a dark, near-black espresso (#332421), providing a strong foundation for text and structural elements. This is complemented by a secondary soft champagne beige (#e2cebb), which brings a lighter, more airy feel to the interface compared to previous high-energy accents. The tertiary terracotta (#a56639) provides a bridge of organic warmth between the dark espresso and the light beige.

A clean, white neutral foundation (#ffffff) serves as the backdrop for the system. This pure white environment allows the "Steel" elements to feel precise and ensures the subtle secondary tones remain legible and elegant. The system operates in a Light color mode, maintaining an open and professional atmosphere.

## Typography
The system uses **Inter** for all typographic roles. Inter provides a highly legible, neutral, and modern foundation that adapts perfectly to both UI labels and long-form body text. 

Headlines are bold and structured, using tighter line heights to create a strong visual hierarchy. Body text is optimized for readability with comfortable line spacing. Labels utilize a slightly heavier weight and increased letter spacing to ensure clarity at small sizes. The scale is designed to be device-agnostic, with headline sizes adjusting for mobile breakpoints where necessary.

## Layout & Spacing
The layout follows a fluid grid system based on a 4px/8px baseline rhythm. The system uses a standard 12-column grid for desktop environments, transitioning to a 4-column grid for mobile devices. 

Margins are set at 24px for comfort, while gutters are 16px to maintain proximity between related content blocks. Padding and margins throughout the UI should always be multiples of the 8px base unit to ensure a consistent vertical and horizontal rhythm.

## Elevation & Depth
Depth is conveyed through a combination of **Tonal Layers** and **Ambient Shadows**. Surfaces use subtle shifts in background color (utilizing the white neutral and lightened versions of the earthy palette) to indicate hierarchy. 

Shadows should be extra-diffused and low-opacity, avoiding harsh blacks in favor of tints derived from the primary espresso color. This creates a soft, natural depth that feels like layers of stone or textured materials. Higher elevation levels are used sparingly for floating actions or modal dialogs, often paired with the secondary champagne or tertiary terracotta for subtle visual recognition.

## Shapes
The design language utilizes a **Rounded** profile. This softens the professional aesthetic, making the interface feel modern and tactile.

Standard UI elements (buttons, inputs) feature a 0.5rem (8px) corner radius. Larger containers, such as cards, utilize a 1rem (16px) radius, while featured surfaces or "xl" components use a 1.5rem (24px) radius. This consistent curvature creates a cohesive visual language across all interactive elements.

## Components
- **Buttons:** Utilize the primary espresso (#332421) for high-emphasis actions to maintain a grounded, professional look. The secondary champagne (#e2cebb) is used for secondary actions or ghost buttons to provide a softer contrast. Tertiary actions use the terracotta (#a56639).
- **Input Fields:** Feature a 0.5rem corner radius with a subtle neutral-colored border. Focused states should use a primary espresso or tertiary terracotta colored border.
- **Cards:** Elevated slightly with ambient shadows and 1rem rounded corners. Use tonal backgrounds or subtle outlines to differentiate between content types against the white background.
- **Chips & Tags:** Use light secondary champagne backgrounds or terracotta tints with dark primary text for high legibility.
- **Lists:** Clean, minimalist separators using low-contrast versions of the primary color to define boundaries without cluttering the view.