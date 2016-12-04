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

    ListModel {
        id: historyModel
        ListElement {command: "Anacondases"}
    }

    Item {
        id: statusModel
        property string age: ""
        property string color: "LightBlue"
        property var kStatusMap: {
            'CONNECTED': 'Gold',
            'RUNNING': 'Gold',
            'IDLE': 'LightGreen',
            'FAILURE': 'Salmon',
            'BLOCKED_ON_WIFI': 'Grey',
            'NOT_CONNECTED': 'DeepPink',
            'DISCONNECTED': 'DeepPink'
        }

        /**
         * Updates the UI text based on the current socket state
         */
        function update() {
            if (!socket.lastRunMs) {
                age = "?";
            } else {
                // If we do know the last run, display elapsed time since then 
                // in most significant time unit
                var delta_sec = (new Date().getTime() - socket.lastRunMs) / 1000;
                if (delta_sec < 60) {
                    age = Math.floor(delta_sec) + "s";
                } else if (delta_sec < 3600) {
                    age = Math.floor(delta_sec / 60) + "m";
                } else if (delta_sec < 86400) {
                    age = Math.floor(delta_sec / 3600) + "h";
                } else {
                    age = Math.floor(delta_sec / 86400) + "d";
                }
            }
            // Use color to display status
            if (socket.status in kStatusMap) {
                color = kStatusMap[socket.status];
            } else {
                console.log("Unknown status: " + socket.status);
                color = "Cyan"
            }
        }

        /**
         * Triggers a recalculation of the last run age.
         */
        Timer {
            running: true
            repeat: true
            triggeredOnStart: true
            interval: 5000
            onTriggered: {
                statusModel.update();
            }
        }
    }

    OverseerSocket {
        id: socket
        history: historyModel
        onStatusChange: statusModel.update()
    }

    Plasmoid.backgroundHints: "NoBackground";
    Plasmoid.preferredRepresentation: Plasmoid.compactRepresentation

    Plasmoid.toolTipMainText: socket.status
    Plasmoid.toolTipSubText: "Last Run: " + socket.lastRunString

    Plasmoid.compactRepresentation: CompactRepresentation {
        text: statusModel.age
        color: statusModel.color
    }
    Plasmoid.fullRepresentation: FullRepresentation {
        status: socket.status
        lastRun: socket.lastRunString
        version: socket.version
        color: statusModel.color
        history: historyModel
        onRunRequested: socket.runNow()
    }
}
