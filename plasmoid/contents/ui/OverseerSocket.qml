 /**
  * KDE Plasma script to monitor Overseer execution status
  *
  * Copyright Jody Sankey 2016
  */

import QtQml 2.2
import QtQuick 2.0
import QtWebSockets 1.0

Item { 
    id: root
    property var last_run_ms: null
    property string status: "NOT CONNECTED"
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
            root.status = status;
            root.last_run_ms = null;
            historyModel.clear();
        }

        /**
        * Parses and stores information from a received packet, updating
        * display state as required
        */
        function receivePacket(packet) {
            var json = JSON.parse(packet);
            if ('status' in json) {
                var updated_start_ms = parseInt(json.last_start_ms);
                if (updated_start_ms !== internal.last_run_ms) {
                    root.last_run_ms = updated_start_ms;
                    socket.sendTextMessage("HISTORY");
                }
                root.status = json.status;
                statusChange();
            } else if (json.length > 0  && "command" in json[0]) {
                historyModel.clear();
                for (var i = 0; i < json.length ; i++) {
                    var lastExec = json[i].executions[json[i].executions.length - 1];
                    historyModel.append({
                        "command": json[i].command,
                        "last_start_ms": lastExec.start_ms,
                        "last_duration_ms": lastExec.end_ms - lastExec.start_ms,
                        "last_exit_code": lastExec.exit_code
                    });
                }
            } else {
                console.log('Received unknown packet: ' + packet);
            }
        }
    }

    ListModel {
        id: historyModel
    }

    WebSocket {
        id: socket
        url: "ws://localhost:4321/overseer"
        onTextMessageReceived: {
            internal.receivePacket(message)
        }
        onStatusChanged: 
            if (socket.status == WebSocket.Open) {
                internal.setLocalStatus("CONNECTED")
                socket.sendTextMessage("STATUS")
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