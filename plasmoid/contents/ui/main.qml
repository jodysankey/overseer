 /**
  * KDE Plasma script to monitor Overseer execution status
  *
  * Copyright Jody Sankey 2016
  */

import QtQml 2.2
import QtQuick 2.0
import QtWebSockets 1.0
import QtQuick.Controls 1.4
import org.kde.plasma.plasmoid 2.0

Item {
    id: root
    property var last_run_ms: null
    property var history: null
    property var disconnect_ms: null

    /**
     * Parses and stores information from a received packet, updating
     * display state as required
     */
    function receivePacket(packet) {
        var json = JSON.parse(packet);
        if ('status' in json) {
            var updated_start_ms = parseInt(json.last_start_ms);
            if (updated_start_ms !== root.last_run_ms) {
                root.last_run_ms = updated_start_ms;
                socket.sendTextMessage("HISTORY");
            }
            statusModel.status = json.status;
            updateDisplay();
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

    /**
     * Sets a state while not able to receive execution state from the server
     */
    function setLocalStatus(status) {
        statusModel.status = status;
        root.last_run_ms = null;
        historyModel.clear();
        updateDisplay();
    }

    /**
     * Updates the UI text based on stored state and current time
     */
    function updateDisplay() {
        if (!updateDisplay.MAP) {
            updateDisplay.MAP = {
                'CONNECTED': 'Gold',
                'RUNNING': 'Gold',
                'IDLE': 'LightGreen',
                'FAILURE': 'Salmon',
                'BLOCKED_ON_WIFI': 'Gold',
                'NOT_CONNECTED': 'DeepPink',
                'DISCONNECTED': 'DeepPink'
            };
        }

        if (!root.last_run_ms) {
            // If we don't know the last run, just display the status
            statusModel.text = statusModel.status;
        } else {
            // If wse do know the last run, display elapsed time since then 
            // in most significant time unit
            var delta_sec = (new Date().getTime() - root.last_run_ms) / 1000;
            if (delta_sec < 60) {
                statusModel.text = Math.floor(delta_sec) + "s";
            } else if (delta_sec < 3600) {
                statusModel.text = Math.floor(delta_sec / 60) + "m";
            } else if (delta_sec < 86400) {
                statusModel.text = Math.floor(delta_sec / 3600) + "h";
            } else {
                statusModel.text = Math.floor(delta_sec / 86400) + "d";
            }
        }
        // Use color to display status
        if (statusModel.status in updateDisplay.MAP) {
            statusModel.color = updateDisplay.MAP[statusModel.status];
        } else {
            console.log("Unknown status: " + statusModel.status);
            statusModel.color = "Cyan"
        }
    }

    /**
     * Returns a friendly formatted string from a JS Date
     */
    function dateString(date) {
        return (date.getFullYear() + "-"
            + ("0" + (date.getMonth() + 1)).slice(-2) + "-"
            + ("0" + date.getDate()).slice(-2) + " "
            + ("0" + date.getHours()).slice(-2)+ ":"
            + ("0" + date.getMinutes()).slice(-2) + ":"
            + ("0" + date.getSeconds()).slice(-2));
    }

    WebSocket {
        id: socket
        url: "ws://localhost:4321/overseer"
        onTextMessageReceived: {
            receivePacket(message)
        }
        onStatusChanged: 
            if (socket.status == WebSocket.Open) {
                setLocalStatus("CONNECTED")
                socket.sendTextMessage("STATUS")
            } else if (socket.status == WebSocket.Closed
                    || socket.status == WebSocket.Error) {
                setLocalStatus("DISCONNECTED")
                root.disconnect_ms = new Date().getTime()
                socket.active = false
            }
        active: true
    }

    ListModel {
        id: historyModel
    }

    Item {
        id: statusModel
        property string text : "NOT_CONNECTED"
        property string status : "NOT_CONNECTED"
        property string color: "LightBlue"
    }

    Timer {
        running: true
        repeat: true
        triggeredOnStart: true
        interval: 15000
        onTriggered: {
            if (socket.active && socket.status == WebSocket.Open) {
                socket.sendTextMessage("STATUS")
            } else if (new Date().getTime() > root.disconnect_ms + 30000) {
                socket.active = true;
            }
        }
    }

    ///Plasmoid.switchWidth: 200
    //Plasmoid.switchHeight: 200
    Plasmoid.compactRepresentation: CompactRepresentation {
        text: statusModel.text
        status: statusModel.status
        color: statusModel.color
        runTime: (!root.last_run_ms ? "N/K" : dateString(new Date(root.last_run_ms)))
        connected: (socket.status == WebSocket.Open)
        onRunRequested: socket.sendTextMessage("RUN")
    }
    Plasmoid.fullRepresentation: FullRepresentation {
        text: statusModel.text
        status: statusModel.status
        color: statusModel.color
        runTime: (!root.last_run_ms ? "N/K" : dateString(new Date(root.last_run_ms)))
        connected: (socket.status == WebSocket.Open)
        onRunRequested: socket.sendTextMessage("RUN")
    }
}
