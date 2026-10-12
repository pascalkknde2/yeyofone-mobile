#!/bin/bash
# Installs (or updates) the YeyoFone API page on the FusionPBX server:
#   /var/www/fusionpbx/app/yeyofone/api.php
# Run from the yeyofone-desktop folder, with SSH set to reach the PBX as root, for example:
#   SSH="ssh root@pbx.example.com" pbx/install-yeyofone-api.sh
# The file is syntax-checked on the server before it replaces the old one.
# Undo: ssh to the PBX and remove /var/www/fusionpbx/app/yeyofone.
set -euo pipefail
: "${SSH:?Set SSH to the ssh command that reaches the PBX, e.g. SSH=\"ssh root@pbx\"}"
here=$(cd "$(dirname "$0")" && pwd)
$SSH 'set -e
dir=/var/www/fusionpbx/app/yeyofone
mkdir -p "$dir"
cat > "$dir/api.php.new"
php -l "$dir/api.php.new"
mv "$dir/api.php.new" "$dir/api.php"
chown -R www-data:www-data "$dir"
chmod 755 "$dir"; chmod 644 "$dir/api.php"
echo "Installed $dir/api.php"' < "$here/yeyofone/api.php"
