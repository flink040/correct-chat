# Third-party notice and publication boundary

Correct Chat 1.0 is an independently authored Fabric mod. Its implementation, user interface,
documentation and assets are original to this project. The maintainer has completed the publication
review and determined that the release contains no source code, binaries, assets or text copied from
earlier chat add-ons.

Historical analysis material is not part of Correct Chat. It remains excluded from version control,
source archives, binary releases and public repository mirrors. The release build verifies that local
configuration, backups and analysis directories are absent from the distributable JAR.

## Audio decoding libraries

The binary distribution contains the unmodified Soundlibs repackagings of JLayer, MP3SPI, JOrbis,
VorbisSPI and Tritonus Share to decode user-selected MP3 and OGG alarm sounds. Soundlibs declares
these artifacts under the GNU Lesser General Public License 2.1. Their separate JAR files are kept
under `META-INF/jars/` in the mod JAR; WAV playback uses the Java Sound API directly. The complete
license is distributed as `META-INF/LICENSES/LGPL-2.1.txt`, and this notice is included as
`META-INF/THIRD_PARTY_NOTICE.md`.
