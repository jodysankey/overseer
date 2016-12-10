 /**
  * Encapsulation of a websocket connected to Overseer
  *
  * Copyright Jody Sankey 2016
  *
  * This software may be modified and distributed under the terms of the MIT license.
  * See the LICENSE.md file for details.
  */
import QtQml 2.2
import QtQuick 2.0
import QtWebSockets 1.0

Item { 
    property var lastRunMs: null
    property var lastRunString: "N/K"
    property var version: "N/K"
    property string status: "NOT_CONNECTED"
    property ListModel history

    signal statusChange()

    /**
     * Requests immediate execution of the commands, provided the socket is connected.
     */
    function runNow() {
        if (socket.status == WebSocket.Open) {
            socket.sendTextMessage("RUN")
        }
    }

    /**
     * Internal timing state and processing that should not need
     * to be accessible from outside the Component.
     */
    Item {
        id: internal
        property var disconnect_ms: null

        /**
        * Sets a state while not able to receive execution state from the server
        */
        function setLocalStatus(status) {
            parent.status = status;
            parent.lastRunMs = null;
            parent.history.clear();
        }

        /**
        * Parses and stores information from a received packet, updating
        * display state as required
        */
        function receivePacket(packet) {
            var json = JSON.parse(packet);
            if ('version' in json) {
                parent.version = json.version
                socket.sendTextMessage("STATUS")
            } else if ('status' in json) {
                var updatedStartMs = parseInt(json.last_start_ms);
                if (updatedStartMs !== parent.lastRunMs) {
                    parent.lastRunMs = updatedStartMs;
                    parent.lastRunString = Qt.formatDateTime(
                        new Date(updatedStartMs), "yyyy-MM-dd hh:mm:ss");
                    socket.sendTextMessage("HISTORY");
                    parent.status = json.status;
                    statusChange();
                } else if (parent.status != json.status) {
                    parent.status = json.status;
                    statusChange();
                }
            } else if (json.length > 0  && "command" in json[0]) {
                history.clear();
                for (var i = 0; i < json.length ; i++) {
                    //parent.history.append({"command": json[i].command});
                    for (var j = json[i].executions.length -1; j >= 0; j--) {
                        var run = json[i].executions[j]
                        parent.history.append({
                            "command": json[i].command,
                            "start": Qt.formatDateTime(new Date(run.start_ms), "yyyy-MM-dd hh:mm"),
                            "duration": Number((run.end_ms - run.start_ms) / 1000).toFixed(1) + " sec",
                            "exitCode": run.exit_code
                        });
                    }
                }
            } else {
                console.log('Received unknown packet: ' + packet);
            }
        }
    }

    WebSocket {
        id: socket
        // TODO(jody): Add a configuration page for port to avoid hardcoding.
        url: "ws://localhost:4321/overseer"
        onTextMessageReceived: {
            internal.receivePacket(message)
        }
        onStatusChanged: 
            if (socket.status == WebSocket.Open) {
                internal.setLocalStatus("CONNECTED")
                socket.sendTextMessage("VERSION")
                statusChange();
            } else if (socket.status == WebSocket.Closed
                    || socket.status == WebSocket.Error) {
                internal.setLocalStatus("DISCONNECTED")
                internal.disconnect_ms = new Date().getTime()
                socket.active = false
                statusChange();
            }
        active: true
    }

    Timer {
        running: true
        repeat: true
        triggeredOnStart: true
        interval: 15000
        onTriggered: {
            if (socket.active && socket.status == WebSocket.Open) {
                socket.sendTextMessage("STATUS")
            } else if (new Date().getTime() > internal.disconnect_ms + 30000) {
                socket.active = true;
            }
        }
    }

}