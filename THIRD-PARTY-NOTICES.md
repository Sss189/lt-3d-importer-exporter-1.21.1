# Upstream and dependencies

This is an unofficial Minecraft 1.21.1 / NeoForge port of Timardo's LT 3D Importer & Exporter.
Upstream: https://github.com/Red-and-White-Fox/LT-3D-Importer-Exporter
Reference snapshot: 1.20.1, commit 926607ff86f9411630fdf83f3b4612ef5f7121bd.
Timardo's MIT license is included in LICENSE.

The adapted triangle slicing, material color sampling and LightObj implementation originate in that project.
The current client integration, bounded task pipeline and baked model export orchestration are adapted for the new APIs.
No Gaksy/CGAL code is included.

Bundled library: de.javagl:obj:0.4.0, https://github.com/javagl/Obj (MIT).
The adapted LightObj sources preserve their own original copyright notices.

Copyright (c) 2008-2015 Marco Hutter - http://www.javagl.de

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

External runtime dependencies (not bundled): Minecraft, NeoForge, LittleTiles, CreativeCore.
