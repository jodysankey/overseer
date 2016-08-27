#!/usr/bin/python3

#================================================================
# Tiny script to print the current status of an overseer server.
# Copyright Jody Sankey 2016
#
# Usage: overseer_check.py PORT_NUM
#================================================================

from datetime import datetime
import json
import socket
import sys

INITIATORS = ('[', '{')
TERMINATORS = (']', '}')

class JsonSocket(object):
  """Defines a socket that receives json in response to commands."""

  def __init__(self, port):
    self.sock = socket.create_connection(('localhost', port))
    #self.file = self.sock.makefile('r')

  def command(self, command):
    """Sends a string command and returns the json response."""
    self.send(command)
    return self.receive()

  def send(self, command):
    """Sends a string command."""
    self.sock.sendall((command + '\n').encode('utf-8'))

  def receive(self):
    """Returns a json response from the socket."""
    # Assume response doesn't contain nested {[]}
    depth, response = 0, ''
    while True:
      c = self.sock.recv(1).decode('utf-8')
      if c in INITIATORS: depth += 1
      elif c in TERMINATORS: depth -= 1
      if depth > 0 or len(response) > 0:
        response += c
      if depth == 0 and len(response) > 0:
        break
    return json.loads(response)

  def close(self):
    """Closes the socket cleanly."""
    self.sock.close()
  
def _formatEpoch(millis): 
  """Returns a nicely formatted string for a supplied epoch offset."""
  return datetime.fromtimestamp(int(millis) / 1000.0).strftime('%Y-%m-%d %H:%M:%S')
  

if __name__ == '__main__':
  if len(sys.argv) !=2:
    print('Please supply port as the only argument')
  else:
    sock = JsonSocket(sys.argv[1])
    try:
      status = sock.command('status')
      history = sock.command('history')
      sock.send('close')
    finally:
      sock.close()
    print(status['status'])
    print('Last run: ' + _formatEpoch(status['last_start_ms']))
    print('')
    for command in history:
      print('Command: ' + command['command'])
      for run in reversed(command['executions']):
        print("    {} ({:0.1f} sec) Exit:{}".format(
            _formatEpoch(run['start_ms']),
            (int(run['end_ms']) - int(run['start_ms'])) / 1000.0,
            run['exit_code']))
  input("\nPress Enter to continue...")

