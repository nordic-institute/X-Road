#!/usr/bin/python


import argparse
from iniparse import ConfigParser

parser = argparse.ArgumentParser(description='Add/modify section/key/value to ini file')
parser.add_argument('-f','--file', help='Ini file name',required=True)
parser.add_argument('-s','--section',help='Section to modify', required=True)
parser.add_argument('-k','--key',help='Key', required=True)
parser.add_argument('-v','--value',help='Value', required=True)
args = parser.parse_args()


config = ConfigParser ()
config.read(args.file)

try:
  config.add_section(args.section)
except:
  pass

config.set(args.section,args.key,args.value)

with open(args.file, 'wb') as configfile:
    config.write(configfile)

