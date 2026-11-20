#!/usr/bin/env bash
# Crea la release de GitHub de la versión actual con el APK adjunto (compilado en el Mac).
# Adjunta también una copia llamada MedicoMem.apk para que este enlace sirva siempre para la última:
#   https://github.com/mosqueteroweb/MedicoMem/releases/latest/download/MedicoMem.apk
#
# Uso (tras bump-version.sh, commit, etiqueta vX.Y.Z subida y ./gradlew assembleRelease):
#   ./scripts/publish-release.sh "resumen con iconos, una línea por cambio"
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

get() { grep "^$1=" version.properties | cut -d= -f2; }
VERSION="$(get VERSION_MAJOR).$(get VERSION_MINOR).$(get VERSION_PATCH)"
APK="app/build/outputs/apk/release/medicomem-$VERSION-release.apk"
[[ -f "$APK" ]] || { echo "No existe $APK: compila antes con ./gradlew assembleRelease" >&2; exit 1; }
git ls-remote --exit-code --tags origin "v$VERSION" >/dev/null || { echo "La etiqueta v$VERSION no está en GitHub" >&2; exit 1; }

SUMMARY="${1:-}"
NOTES="📦 **APK:** \`medicomem-$VERSION-release.apk\` (Android 7.0 o superior). Descárgalo en el móvil y ábrelo para instalar."
if [[ -n "$SUMMARY" ]]; then
  NOTES="$NOTES

$(printf '%s\n' "$SUMMARY" | sed 's/^/- /')"
fi

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
cp "$APK" "$TMP/MedicoMem.apk"
gh release create "v$VERSION" "$APK" "$TMP/MedicoMem.apk" --title "MedicoMem $VERSION" --notes "$NOTES"
echo "Publicada: https://github.com/mosqueteroweb/MedicoMem/releases/tag/v$VERSION"
