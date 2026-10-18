#!/bin/sh
# Wraps src/signedvault.html into a standalone page (index.html) for any static host.
set -e
{
  printf '<!doctype html>\n<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">\n'
  printf '<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0}img{max-width:100%%}[hidden]{display:none!important}</style>\n</head><body>\n'
  cat src/signedvault.html
  printf '\n</body></html>\n'
} > index.html
echo "Built index.html"
