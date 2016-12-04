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

        if (!socket.last_run_ms) {
            // If we don't know the last run, just display the status
            statusModel.text = socket.status;
        } else {
            // If wse do know the last run, display elapsed time since then 
            // in most significant time unit
            var delta_sec = (new Date().getTime() - socket.last_run_ms) / 1000;
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
        if (socket.status in updateDisplay.MAP) {
            statusModel.color = updateDisplay.MAP[socket.status];
        } else {
            console.log("Unknown status: " + socket.status);
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

    Item {
        id: statusModel
        property string text : "NOT_CONNECTED"
        //property string status : "NOT_CONNECTED"
        property string color: "LightBlue"
    }

    OverseerSocket {
        id: socket
        onStatusChange: updateDisplay()
    }

    Plasmoid.backgroundHints: "NoBackground";
    Plasmoid.preferredRepresentation: Plasmoid.compactRepresentation

    Plasmoid.toolTipMainText: socket.status
    Plasmoid.toolTipSubText: "Last Run: " +  (!socket.last_run_ms ? "N/K" : dateString(new Date(socket.last_run_ms)))

    Plasmoid.compactRepresentation: CompactRepresentation {
        text: statusModel.text
        color: statusModel.color
    }
    Plasmoid.fullRepresentation: FullRepresentation {
        text: statusModel.text
        status: socket.status
        color: statusModel.color
        runTime: (!socket.last_run_ms ? "N/K" : dateString(new Date(socket.last_run_ms)))
        //connected: (socket.status == WebSocket.Open)
        //onRunRequested: socket.sendTextMessage("RUN")
    }
}
