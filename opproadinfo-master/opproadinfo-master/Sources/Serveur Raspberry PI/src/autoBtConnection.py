""" AutomaticBluetoothConnection

Ce script python 3 permet aux appareils Bluetooth à proximité du raspberry (portable, tablette, etc..)
de se connecter automatiquement à celui-ci. Aucunes interventions humaines en ligne de commande ne sont nécessaires.

Il sera lancé à chaque démarrage du raspberry et sera en écoute en boucle jusqu'à son débranchement.
"""

#pexpect -> librairie permettant de lancer des processus fils, les contrôler et réagir à ce qu'ils affichent
#re -> Librairie permettant de manipuler des expressions régulières avec un script python
import pexpect

# Initialisation du processus enfant
child_process = pexpect.spawn('bluetoothctl') #Processus enfant lancant la service "bluetoothctl"
child_process.timeout = None #Pas de timeout, en écoute en boucle des appareils qui tentent de se connecter

# Initialisation des parametres bluetooth
child_process.expect("[bluetooth]") #attend de lire une sortie avec un pattern [bluetooth] qui correspond au prompt (ecriture de commande possible si détécté)
child_process.sendline("power on") #Activation du Bluetooth du raspberry
child_process.expect("[bluetooth]")
child_process.sendline("discoverable on") #Activer l'apparition du raspberry sur la liste des appareils Bluetooth des autres appareils
child_process.expect("[bluetooth]")
child_process.sendline("pairable on") #Appairement possible du raspberry à un appareil Bluetooth
child_process.expect("[bluetooth]")
child_process.sendline("agent off") #Desactive l'agent courant (agent = gère les connexions Bluetooth)
child_process.expect("[bluetooth]")
child_process.sendline("agent DisplayYesNo") #On remplace par un agent qui, lorsque un téléphone tente de se connecter, demande les autorisations dans le terminal (yes / no)
child_process.expect("[bluetooth]")
child_process.sendline("default-agent") #On active l'agent
child_process.expect("[bluetooth]")

while True:
    #On attend ici que le prompteur demande de saisir (yes/no) pour autoriser une connexion Bluetooth entre un appareil et le Raspberry
    output = child_process.expect([".*Confirm.*passkey*",".*Authorize.*service.*(yes/no).*"]) #deux possibilités d'affichage
    #On accepte toutes les demandes
    child_process.sendline("yes") 
child_process.close()
