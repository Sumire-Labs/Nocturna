<img src="modern/src/main/resources/icon.png" width="128">

# Nocturna

Minecraft Forge 1.20.1 向けのFOSSの強力なレンダリングMod。これは個人的なModPackのために維持されています。

## 互換性なし
- Embeddium Extra
- Iris Shader
- Sodium Dynamic Lights
- More Culling
- Zoomify

## クレジット

* JellySquid & the CaffeineMC team, for making Sodium in the first place, without which this project would not be possible
* Asek3, for the initial port to Forge
* XFactHD, for providing a list of gamebreaking Rubidium issues to start this work off, and for testing early builds
* Pepper, for their invaluable assistance with getting Forge lighting to work on Sodium 0.5
* Ven ([@basdxz](https://github.com/basdxz)), for help with translucency sorting, suggesting the general approach for async occlusion culling, and other suggestions during development
* rdh, for leading several key improvements, including faster & better occlusion culling, better AO rendering, etc
* douira, for researching and writing the [paper](https://douira.dev/assets/document/douira-master-thesis.pdf) on which the translucency sorting v3 implementation is based
* grondag, for developing [bitraster](https://github.com/vram-guild/bitraster), the library we vendor for rasterized occlusion culling
* embeddedt, This would not have been possible without Embeddium and Celeritas, both created by him. My deepest thanks go to him.