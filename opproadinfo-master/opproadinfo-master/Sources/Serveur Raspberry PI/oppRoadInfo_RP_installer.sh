#!/bin/bash

echo "Configuration du réseau broadcast..."

echo "1 - Configuration de wlan0 ... "

mkdir /etc/network/interfaces.d

echo "auto wlan0
iface wlan0 inet6 static
    address fdaa::d41c:23ff:fe15:c7e0/64
    wireless-mode ad-hoc
    wireless-essid adhonet
    wireless-channel 5
    post-up ip -6 route add ff02:ca5a::/32 dev wlan0" > /etc/network/interfaces.d/wlan0


echo " 2 - Configuration du raspberry pour le mode adhoc ..."

echo "
#!/bin/bash
#-------------------------------------------------------------------------------
# Configuration of the Raspberry-pi zero
#
# Author: Nicolas Le Sommer
# Version: 1.0
# Date: 31/03/2021
#-------------------------------------------------------------------------------

#PKG_TO_REMOVE=\"dhcpcd5 avahi-daemon samba-common\"
PKG_TO_REMOVE=\"\"
#PKG_TO_INSTALL=\"hostapd wpasupplicant isc-dhcp-server openjdk-8-jre-headless gpsd python-gps firmware-realtek firmware-linux firmware-linux-nonfree firmware-atheros ntpdate rpi-update vim fping gettext\"
PKG_TO_INSTALL=\"wpasupplicant firmware-realtek firmware-linux firmware-linux-nonfree firmware-atheros ntpdate rpi-update vim fping gettext i2c-tools python-bluez python-dev python-setuptools bluez-tools at\"

#-------------------------------------------------------------------------------
# Install missing debian packages and remove unnecessary ones
#-------------------------------------------------------------------------------
pkg_install(){
    apt-get update
    apt-get dist-upgrade
    apt-get install -y $PKG_TO_INSTALL
}


#-------------------------------------------------------------------------------
# Perform an update of clock and the firmware
#-------------------------------------------------------------------------------
set_date_time(){
    if [[ ! \$(grep -q ntp1 /etc/systemd/timesyncd.conf) ]]
    then
        echo \"Updating /etc/systemd/timesyncd.conf\"
        echo \"NTP=ntp1.univ-ubs.fr\" >> /etc/systemd/timesyncd.conf
    fi
}

#-------------------------------------------------------------------------------
# Set time zone
#-------------------------------------------------------------------------------
set_time_zone(){
    if [[ ! \$(grep -q Paris /etc/timezone) ]]
    then
        echo \"Updating /etc/timezone\"
        rm /etc/localtime
        echo \"Europe/Paris\" > /etc/timezone
        dpkg-reconfigure -f noninteractive tzdata
    fi
}

#-------------------------------------------------------------------------------
# Disabling useless services
#-------------------------------------------------------------------------------
disable_useless_services(){
    for s in dhcpcd.service wpa_supplicant.service fake-hwclock.service avahi-daemon.service avahi-daemon.socket exim4.service wifi-country.service ; do
        res=$(systemctl is-enabled $s 2> /dev/null)
        if [[ \$res == \"enabled\" ]]
        then
            echo \"Disabling ${s}\"
            systemctl stop $s
            systemctl disable $s
        fi
    done
}

#-------------------------------------------------------------------------------
# Main
#-------------------------------------------------------------------------------
if [ \$(id -u) -ne 0 ]; then
    echo \"The script must be exectuted as root or you must use the command sudo...\"
    exit 1
fi

set_time_zone
set_date_time
pkg_install
disable_useless_services
" > /root/config_rpi.sh

chmod +x /root/config_rpi.sh
/root/config_rpi.sh
rm /etc/wpa_supplicant/wpa_supplicant.conf
rfkill unblock 0

#Installation des packages
apt update && echo "#### UPDATE FINISH" && apt-get -y install libbluetooth-dev python-dev && echo "#### APT INSTALL FINISH" && pip3 install PyBluez==0.22 && echo "#### PIP INSTALL FINISH"

#Repertoire d'installation
mkdir OppRoadInfoNetwork && cd OppRoadInfoNetwork
oppPath=`pwd`

#configuration du package bluetooth
sed -i "s/#DiscoverableTimeout = 0/DiscoverableTimeout = 0/g" /etc/bluetooth/main.conf
sed -i "s/ExecStart=\/usr\/lib\/bluetooth\/bluetoothd/ExecStart=\/usr\/lib\/bluetooth\/bluetoothd -C/g" /lib/systemd/system/bluetooth.service
echo "#### Configuration du package bluetooth terminée"

#fichier serveur rfcomm
echo "
# file: rfcomm-server.py
# auth: OppRoadInfo Groupe 2

from bluetooth import *
import threading
import time
import sqlite3
import json
import socket
import struct
from math import sin, cos, sqrt, atan2, radians

def bluetoothConnection():
    global server_sock, client_sock

    server_sock = BluetoothSocket( RFCOMM )
    server_sock.bind((\"\",PORT_ANY))
    server_sock.listen(1)

    port = server_sock.getsockname()[1]

    uuid = \"94f39d29-7d6d-437d-973b-fba39e49d4ee\"

    advertise_service( server_sock, \"SampleServer\",
                    service_id = uuid,
                    service_classes = [ uuid, SERIAL_PORT_CLASS ],
                    profiles = [ SERIAL_PORT_PROFILE ],)

    print(\"Waiting for connection on RFCOMM channel %d\" % port)
    client_sock, client_info = server_sock.accept()
    print(\"Accepted connection from \", client_info)

def isSameEvent(event1, event2):

    if (event1.get('type') == event2.get('type')) :
        R = 6373000.0

        lat1 = radians(event1.get('latitude'))
        lon1 = radians(event1.get('longitude'))
        lat2 = radians(event2.get('latitude'))
        lon2 = radians(event2.get('longitude'))

        dlon = lon2 - lon1
        dlat = lat2 - lat1

        a = sin(dlat / 2)**2 + cos(lat1) * cos(lat2) * sin(dlon / 2)**2
        c = 2 * atan2(sqrt(a), sqrt(1 - a))

        distance = R * c

        if (distance <= 50): return event2
        else : return None

    else : return None

def getAllEvents(cursor):
    tupleEvents = cursor.execute(\"SELECT * FROM event\").fetchall()
    events = []

    for event in tupleEvents:
        events.append({'id': int(event[0]), 'latitude': float(event[1]), 'longitude': float(event[2]), 'type': event[3], 'end': float(event[4])})
    return events

def getEventFromId(id, cursor):
    event = cursor.execute('''SELECT * FROM event WHERE id=?''', (id,)).fetchone()

    if event != None:
        return {'id': int(event[0]), 'latitude': float(event[1]), 'longitude': float(event[2]), 'type': event[3], 'end': float(event[4])}
    else:
        return None

def isExists(event, cursor):

    allEvents = getAllEvents(cursor)

    for e in allEvents:
        sameEvent = isSameEvent(event, e)
        if sameEvent != None : return sameEvent

    return None

def addEvent(event, cursor, conn):
    print(\"Ajout à la base : \" + str(event))
    cursor.execute(\"\"\"INSERT INTO event(latitude, longitude, end, type) VALUES(:latitude, :longitude, :end, :type)\"\"\",
    {\"latitude\": str(event.get('latitude')), \"longitude\": str(event.get('longitude')), \"end\": str(event.get('end')), \"type\": event.get('type')})

    conn.commit()

def updateEnd(event, end, cursor, conn):
    cursor.execute('''UPDATE event SET end=? WHERE id=?''', (end, event.get('id')) )
    conn.commit()
    print(\"Événement N°\" + str(event.get('id')) + \", UPDATE end = \" + str(end))

def sendEvent(event):
    global client_sock
    try:
        client_sock.send(\"{'id': \" + str(event.get('id')) + \", 'longitude': \" + str(event.get('longitude')) + \", 'latitude': \" + str(event.get('latitude')) + \" , 'type': \" + event.get('type') + \", 'end' : \" + str(event.get('end')) + \"}\")
        print(\"RP -> TEL : \" + str(event))
    except BluetoothError:
        print(\"Erreur de connexion avec le Tel\")
        print(\"Reconnexion en cours...\")
        bluetoothConnection()

def deleteEvent(event, cursor, conn):
    print('Event supprimé : ', end='')
    print(event)

    cursor.execute(\"\"\"DELETE FROM event WHERE id=?\"\"\", (event.get('id'),) )
    conn.commit()

def sendBroadcast(events, sock, sock_addr):
    print(\"RP -> RPs : \", end='')
    print(str(events))
    sock.sendto(str(events).encode(), sock_addr)

####

#Appelé par le listener de broadcast
def eventBroadcastReception(event, cursor, conn):
    sameEvent = isExists(event, cursor)
    if sameEvent != None:

        if event.get('end') > sameEvent.get('end'):
            updateEnd(sameEvent, event.get('end'), cursor, conn)

    else:
        addEvent(event, cursor, conn)
        sendEvent(event)

#Appelé par le listener du tel il recoit un event
def eventTelReception(event, cursor, conn):
    addEvent(event, cursor, conn)

#Appelé par le listener du tel quand il reçoit une réponse à une demande de confirmation d'event
def confirmationResponse(response, cursor, conn):
    eventId = response.get('id')

    event = getEventFromId(eventId, cursor)

    print(\"Confirmation : \" + str(event))

    if event != None :

        if (event.get('type') == 'traffic'):
            delay = 1800
        elif (event.get('type') == 'work'):
            delay = 7200
        elif (event.get('type') == 'accident'):
            delay = 900
        elif (event.get('type') == 'check'):
            delay = 900
        else:
            delay = 1800

        if response.get('reliability') == 1:
            updateEnd(event, event.get('end') + delay, cursor, conn)

        elif response.get('reliability') == -1:
            updateEnd(event, event.get('end') - delay, cursor, conn)

        else:
            print(\"Aie, pas de bonne reliability\")


# Listen to the phone
class ListenPhoneThread(threading.Thread):

    def __init__(self, client_sock):
        threading.Thread.__init__(self) # Ne pas oublier cette ligne bordel !
        self.client_sock = client_sock

    def run(self):

        while True:
            try:
                data = client_sock.recv(1024)
                conn = sqlite3.connect('bdd.db')
                cursor = conn.cursor()

                if len(data) != 0:
                    data = str(data).replace(\"b'\", \"\").replace(\"}'\", \"}\")
                    print('TEL -> RP : ' + data)
                    data = json.loads(data)

                    if data.get('id') != None:
                        confirmationResponse(data, cursor, conn)
                    else:
                        eventTelReception(data, cursor, conn)
                conn.close()

            except IOError:
                print(\"IOError\")
                conn.close()
                bluetoothConnection()
                pass

            except KeyboardInterrupt:
                print(\"disconnected\")
                client_sock.close()
                server_sock.close()
                conn.close()
                break

# Send event data base in broadcast
class SendBroadcastThread(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)

    def run(self):
        while True:
            time.sleep(1)
            conn = sqlite3.connect('bdd.db')
            cursor = conn.cursor()
            ifn=\"wlan0\"
            sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
            sock_addr = socket.getaddrinfo(\"ff02::1\", 5000, socket.AF_INET6, socket.SOCK_DGRAM)[0][4]

            # Set multicast interface
            ifi = socket.if_nametoindex(ifn)
            ifis = struct.pack(\"I\", ifi)
            sock.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_MULTICAST_IF, ifis)

            events = getAllEvents(cursor)

            for event in events:
                if (event.get('end') < time.time()):
                    deleteEvent(event, cursor, conn)

            sendBroadcast(events, sock, sock_addr)
            conn.close()

# Send event data base in broadcast
class ListenBroadcastThread(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)

    def run(self):
        local_addr = \"::\"
        mcast_addr = \"ff02::1\"
        mcast_port = 5000
        ifn = \"wlan0\"

        # Create socket
        sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)

        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)

        # Set multicast interface
        ifi = socket.if_nametoindex(ifn)
        ifis = struct.pack(\"I\", ifi)
        sock.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_MULTICAST_IF, ifis)

        # Set multicast group to join
        group = socket.inet_pton(socket.AF_INET6, mcast_addr) + ifis
        sock.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_JOIN_GROUP, group)

        sock_addr = socket.getaddrinfo(local_addr, mcast_port, socket.AF_INET6, socket.SOCK_DGRAM)[0][4]
        sock.bind(sock_addr)

        while True:
            data, src = sock.recvfrom(1024)
            events = data.decode().replace('\'', '\"')
            conn = sqlite3.connect('bdd.db')
            cursor = conn.cursor()

            try:
                print('RPs -> RP : ', end='')
                print(events)

                events = json.loads(events)
                
                for event in events:
                    eventBroadcastReception(event, cursor, conn)

                conn.close()
            except json.decoder.JSONDecodeError:
                print('Message received not in conformity')
                conn.close()

try:
    with open('bdd.db') as file:
        print('Data Base is already created')

except IOError:
    print('Create the database')

    conn = sqlite3.connect('bdd.db')

    sql = \"\"\"CREATE TABLE \"event\" (
        \"id\" INTEGER,
        \"latitude\" REAL NOT NULL,
        \"longitude\" REAL NOT NULL,
        \"type\" TEXT NOT NULL,
        \"end\" REAL NOT NULL,
        PRIMARY KEY(\"id\" AUTOINCREMENT)
    );\"\"\"

    cursor = conn.cursor()
    cursor.execute(sql)
    conn.close()

server_sock = None
client_sock = None

bluetoothConnection()

ListenPhoneThread(client_sock).start()
ListenBroadcastThread().start()
SendBroadcastThread().start()
" > rfcomm-server.py

#fichier connexion bluetooth automatique
echo "#### Create autoBtConnection.py in OppRoadInfoNetwork..."
echo "\"\"\" AutomaticBluetoothConnection

Ce script python 3 permet aux appareils Bluetooth à proximité du raspberry (portable, tablette, etc..)
de se connecter automatiquement à celui-ci. Aucunes interventions humaines en ligne de commande ne sont nécessaires.

Il sera lancé à chaque démarrage du raspberry et sera en écoute en boucle jusqu'à son débranchement.
\"\"\"

#pexpect -> librairie permettant de lancer des processus fils, les contrôler et réagir à ce qu'ils affichent
#re -> Librairie permettant de manipuler des expressions régulières avec un script python
import pexpect

# Initialisation du processus enfant
child_process = pexpect.spawn('bluetoothctl') #Processus enfant lancant la service \"bluetoothctl\"
child_process.timeout = None #Pas de timeout, en écoute en boucle des appareils qui tentent de se connecter

# Initialisation des parametres bluetooth
child_process.expect(\"[bluetooth]\") #attend de lire une sortie avec un pattern [bluetooth] qui correspond au prompt (ecriture de commande possible si détécté)
child_process.sendline(\"power on\") #Activation du Bluetooth du raspberry
child_process.expect(\"[bluetooth]\")
child_process.sendline(\"discoverable on\") #Activer l'apparition du raspberry sur la liste des appareils Bluetooth des autres appareils
child_process.expect(\"[bluetooth]\")
child_process.sendline(\"pairable on\") #Appairement possible du raspberry à un appareil Bluetooth
child_process.expect(\"[bluetooth]\")
child_process.sendline(\"agent off\") #Desactive l'agent courant (agent = gère les connexions Bluetooth)
child_process.expect(\"[bluetooth]\")
child_process.sendline(\"agent DisplayYesNo\") #On remplace par un agent qui, lorsque un téléphone tente de se connecter, demande les autorisations dans le terminal (yes / no)
child_process.expect(\"[bluetooth]\")
child_process.sendline(\"default-agent\") #On active l'agent
child_process.expect(\"[bluetooth]\")

while True:
    #On attend ici que le prompteur demande de saisir (yes/no) pour autoriser une connexion Bluetooth entre un appareil et le Raspberry
    output = child_process.expect([\".*Confirm.*passkey*\",\".*Authorize.*service.*(yes/no).*\"]) #deux possibilités d'affichage
    #On accepte toutes les demandes
    child_process.sendline(\"yes\") 
child_process.close()" > autoBtConnection.py

echo "#### Create oppRoadInfo.service..."
echo "[Unit]
Description=OppRoadInfo
After=network-online.target
 
[Service]
User=root
Group=root
UMask=007
WorkingDirectory=`echo $oppPath`
 
ExecStart=/bin/sh -c '/usr/bin/python3 rfcomm-server.py | /usr/bin/python3 autoBtConnection.py'
 
Restart=on-failure
 
# Configures the time to wait before service is stopped forcefully.
TimeoutStopSec=300
 
[Install]
WantedBy=multi-user.target" > /etc/systemd/system/oppRoadInfo.service

systemctl enable oppRoadInfo.service && echo "#### Service enabled"

# Change le nom de l'appareil
echo "#### Change HOST NAME"
name=OppRoadInfo-$RANDOM
sudo sed -i "s/raspberrypi/$name/g" /etc/hostname
sudo sed -i "s/raspberrypi/$name/g" /etc/hosts

echo "############### Raspberry name : $name ####################"
echo "############### Installation success #####################"
echo "############### Reboot in 5 seconds ######################" 

sleep 5

reboot
