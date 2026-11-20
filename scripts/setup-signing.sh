#!/usr/bin/env bash
# Se ejecuta UNA sola vez: crea la clave de firma de MedicoMem en ~/.config/medicomem/ (fuera del repositorio).
# Haz copia de esa carpeta: sin la clave no se pueden instalar actualizaciones encima de la app.
set -euo pipefail
DIR="$HOME/.config/medicomem"
STORE="$DIR/medicomem-release.jks"
[[ ! -e "$STORE" ]] || { echo "Ya existe $STORE: no se sobrescribe." >&2; exit 1; }
KEYTOOL="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}/bin/keytool"

mkdir -p "$DIR"; chmod 700 "$DIR"
PASS="$(openssl rand -hex 24)"
"$KEYTOOL" -genkeypair -keystore "$STORE" -storetype PKCS12 -alias medicomem -keyalg RSA -keysize 4096 \
  -validity 20000 -dname "CN=MedicoMem" -storepass "$PASS" -keypass "$PASS"
printf 'KEYSTORE_PATH=%s\nKEYSTORE_PASSWORD=%s\nKEY_ALIAS=medicomem\nKEY_PASSWORD=%s\n' "$STORE" "$PASS" "$PASS" > "$DIR/signing.properties"
chmod 600 "$STORE" "$DIR/signing.properties"
echo "Hecho. Clave en $STORE (haz una copia de seguridad de $DIR)."
