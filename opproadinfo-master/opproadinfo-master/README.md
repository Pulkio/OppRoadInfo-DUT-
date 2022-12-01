**Groupe B :** 
* Chef de projet : Guillaume COBAT
* Chef de projet adjoint : Matisse PEDRON
* Responsable de la communication : Sylvain BUCHE
* Responsable de la documentation : Pierre BOIVENT 
* Responsable des tests : Louis-Xavier GODET



**Description du projet :**

**Acronyme du projet:**
OppRoadInfo

**Intitulé du projet:** 
Réseau pair-à-pair opportuniste pour une info trafic collaborative reposant sur des dispositifs IoT

**Coordonnées du client:**
Nicolas Le Sommer |
email: nicolas.le-sommer@univ-ubs.fr |
Équipe CASA, Laboratoire IRISA

**Résumé du projet:**

L'objectif du projet est de créer une application d'info trafic en temps réel permettant à un conducteur de connaître le trafic routier dans une zone géographique proche de sa position. Le rayon de cette zone géographique devra pouvoir être défini par l'utilisateur. Cette zone permettra également de restreindre géographiquement la dissémination des données de trafic dans le réseau mobile ad hoc constitué par les dispositifs mobiles embarqués utilisés par les conducteurs. Ces dispositifs seront mis en œuvre en utilisant des ESP32 ou des Raspeberry-pi zéro capables de communiquer en Wi-FI, en Bluetooth et/ou en LoRa. Ils permettront d'échanger des données avec le smartphone du conducteur en Bluetooth, afin d'afficher les informations de trafic sur l'écran de celui-ci, et de diffuser dans le réseau la position de l'utilisateur, sa vitesse de déplacement et d'autres informations qu'il pourra définir via son smartphone (accident, bouchon, ralentissement, ...). L'application installée sur le smartphone de l'utilisateur pourra calculer la densité de véhicule dans une zone donnée et la vitesse de déplacement de celles-ci pour identifier automatiquement un ralentissement par exemple. Il sera nécessaire de s'appuyer sur des données cartographiques pour connaître la limitation de vitesse. OpenStreetMap pourra être utilisé dans cette optique.   

**Comment installer et lancer l'application ?**

Le document "Manuel installation" décrit toutes les étapes à suivre pour installer l'application. 
Il est disponible juste ici. : [Manuel-Installation.pdf](https://gitlab.com/opproadinfo_boivent_buche_cobat_godet/opproadinfo/-/blob/master/Delivrables/Manuel_Installation_-_OppRoadInfo.pdf)

La dernière version de l'APK est disponible ici : [OppRoadInfo.apk](https://gitlab.com/opproadinfo_boivent_buche_cobat_godet/opproadinfo/-/blob/master/Delivrables/OppRoadInfo.apk)
