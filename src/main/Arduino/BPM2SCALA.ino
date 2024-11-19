const int sensorPin = A0;           // Pin où le capteur de rythme cardiaque est connecté
int sensorValue = 0;                // Valeur lue par le capteur
int seuil = 0;                      // Seuil dynamique pour détecter un battement
int valeurMax = 0;                  // Valeur maximale
int valeurMin = 1023;               // Valeur minimale
unsigned long dernierBattement = 0; // Temps du dernier battement détecté
int battementsParSeconde = 0;       // Nombre de battements dans la seconde actuelle
int tamponBPM[15] = {0};            // Tampon circulaire pour stocker les battements par seconde
int indexTampon = 0;                // Index du tampon circulaire
int secondesEcoulees = 0;           // Nombre de secondes écoulées
unsigned long tempsPrecedent = 0;   // Temps précédent pour la mise à jour
unsigned long tempsMinMaxReset = 0; // Temps pour réinitialiser min/max
float marge = 0.35;                 // Marge pour ajuster le seuil dynamique
int seuilDetectionDoigt = 2;        // Amplitude minimale pour détecter un doigt
bool recording = false;             // Indicateur d'enregistrement actif

void setup()
{
  Serial.begin(9600); // Initialisation de la communication série
}

void loop()
{
  // Lecture des commandes série depuis Scala
  if (Serial.available() > 0)
  {
    char command = Serial.read();
    if (command == 'E')
    { // Commande pour commencer l'enregistrement
      recording = true;
      battementsParSeconde = 0;
      indexTampon = 0;
      secondesEcoulees = 0;
      Serial.println("Enregistrement démarré");
    }
    else if (command == 'T')
    { // Commande pour terminer l'enregistrement
      recording = false;

      // Calcul du rythme cardiaque moyen (BPM)
      int totalBPM = 0;
      for (int i = 0; i < secondesEcoulees; i++)
      {
        totalBPM += tamponBPM[i];
      }
      int bpmMoyen = (totalBPM * 4) / (secondesEcoulees > 0 ? secondesEcoulees : 1);

      // Envoi du BPM à Scala
      Serial.print("BPM: ");
      Serial.println(bpmMoyen);
    }
  }

  // Si l'enregistrement est actif, on continue de mesurer le rythme cardiaque
  if (recording)
  {
    sensorValue = analogRead(sensorPin);

    // Réinitialiser les valeurs min/max toutes les 10 secondes
    if (millis() - tempsMinMaxReset > 10000)
    {
      valeurMax = 0;
      valeurMin = 1023;
      tempsMinMaxReset = millis();
    }

    // Mise à jour des valeurs min/max
    if (sensorValue > valeurMax)
      valeurMax = sensorValue;
    if (sensorValue < valeurMin)
      valeurMin = sensorValue;

    int amplitude = valeurMax - valeurMin;
    seuil = (valeurMax + valeurMin) / 2 + amplitude * marge;

    // Vérifier la présence d'un doigt et détecter un battement
    if (amplitude >= seuilDetectionDoigt)
    {
      if (sensorValue > seuil && millis() - dernierBattement > 300)
      {
        battementsParSeconde++;
        dernierBattement = millis();
      }
    }

    // Mise à jour des données toutes les secondes
    if (millis() - tempsPrecedent >= 1000)
    {
      tamponBPM[indexTampon] = battementsParSeconde;
      battementsParSeconde = 0;
      indexTampon = (indexTampon + 1) % 15;

      if (secondesEcoulees < 15)
        secondesEcoulees++;
      tempsPrecedent = millis();
    }

    delay(10); // Délai court pour stabiliser les lectures
  }
}
