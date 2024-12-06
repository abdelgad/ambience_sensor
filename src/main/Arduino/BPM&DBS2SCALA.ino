// Capteur cardiaque
const int sensorPin = A0;           // Pin pour le capteur de rythme cardiaque
int sensorValue = 0;                // Valeur lue par le capteur
int seuil = 0;                      // Seuil dynamique pour détecter un battement
int valeurMax = 0;                  // Valeur maximale
int valeurMin = 1023;               // Valeur minimale
unsigned long dernierBattement = 0; // Temps du dernier battement détecté
int battementsParSeconde = 0;       // Nombre de battements dans la seconde actuelle
int tamponBPM[15] = {0};            // Tampon circulaire pour les battements
int indexTampon = 0;                // Index du tampon
int secondesEcoulees = 0;           // Nombre de secondes écoulées
unsigned long tempsPrecedent = 0;   // Temps précédent pour la mise à jour
unsigned long tempsMinMaxReset = 0; // Temps pour réinitialiser min/max
float marge = 0.35;                 // Marge pour ajuster le seuil dynamique
int seuilDetectionDoigt = 2;        // Amplitude minimale pour détecter un doigt
bool recording = false;             // Indicateur d'enregistrement actif

// Microphone
const int micPin = A5;        // Pin pour le micro
const int sampleWindow = 250; // Durée de l'échantillonnage pour le son en ms
unsigned int sample = 0;
const int maxAudioBufferSize = 100; // Tampon pour stocker les décibels par 250 ms
float audioBuffer[maxAudioBufferSize] = {0};
int audioBufferIndex = 0;   // Index du tampon circulaire pour les décibels
int totalAudioSegments = 0; // Nombre total de segments enregistrés

void setup()
{
  Serial.begin(9600); // Initialise la communication série
}

void loop()
{
  // Lecture des commandes série
  if (Serial.available() > 0)
  {
    char command = Serial.read();
    if (command == 'E')
    { // Commencer l'enregistrement
      recording = true;
      battementsParSeconde = 0;
      indexTampon = 0;
      secondesEcoulees = 0;
      totalAudioSegments = 0;
      audioBufferIndex = 0;
      Serial.println("Enregistrement démarré");
    }
    else if (command == 'T')
    { // Terminer l'enregistrement
      recording = false;

      // Calcul du rythme cardiaque moyen (BPM)
      int totalBPM = 0;
      for (int i = 0; i < secondesEcoulees; i++)
      {
        totalBPM += tamponBPM[i];
      }
      int bpmMoyen = (totalBPM * 4) / (secondesEcoulees > 0 ? secondesEcoulees : 1);

      // Envoi des données à Scala
      Serial.print("BPM: ");
      Serial.println(bpmMoyen);

      // Envoi des décibels moyensS
      Serial.print("DBS: ");
      for (int i = 0; i < totalAudioSegments; i++)
      {
        Serial.print(audioBuffer[i]);
        if (i < totalAudioSegments - 1)
          Serial.print(", ");
      }
      Serial.println();
    }
  }

  // Si l'enregistrement est actif
  if (recording)
  {
    // *** Partie rythme cardiaque ***
    sensorValue = analogRead(sensorPin);

    if (millis() - tempsMinMaxReset > 10000)
    {
      valeurMax = 0;
      valeurMin = 1023;
      tempsMinMaxReset = millis();
    }

    if (sensorValue > valeurMax)
      valeurMax = sensorValue;
    if (sensorValue < valeurMin)
      valeurMin = sensorValue;

    int amplitude = valeurMax - valeurMin;
    seuil = (valeurMax + valeurMin) / 2 + amplitude * marge;

    if (amplitude >= seuilDetectionDoigt)
    {
      if (sensorValue > seuil && millis() - dernierBattement > 300)
      {
        battementsParSeconde++;
        dernierBattement = millis();
      }
    }

    if (millis() - tempsPrecedent >= 1000)
    {
      tamponBPM[indexTampon] = battementsParSeconde;
      battementsParSeconde = 0;
      indexTampon = (indexTampon + 1) % 15;

      if (secondesEcoulees < 15)
        secondesEcoulees++;
      tempsPrecedent = millis();
    }

    // *** Partie son ***
    float peakToPeak = measureSoundLevel();
    int db = map(peakToPeak, 20, 900, 30, 90);

    // Stockage dans le tampon circulaire des décibels moyens
    audioBuffer[audioBufferIndex] = db;
    audioBufferIndex = (audioBufferIndex + 1) % maxAudioBufferSize;
    if (totalAudioSegments < maxAudioBufferSize)
      totalAudioSegments++;
  }

  delay(10); // Délai court pour stabiliser les lectures
}

// Fonction pour mesurer le niveau sonore en décibels
float measureSoundLevel()
{
  unsigned long startMillis = millis(); // Début de l'échantillonnage
  float peakToPeak = 0;                 // Amplitude crête-à-crête
  unsigned int signalMax = 0;
  unsigned int signalMin = 1024;

  while (millis() - startMillis < sampleWindow)
  {
    sample = analogRead(micPin); // Lecture du micro
    if (sample < 1024)
    { // Ignorer les valeurs aberrantes
      if (sample > signalMax)
      {
        signalMax = sample; // Mise à jour du maximum
      }
      else if (sample < signalMin)
      {
        signalMin = sample; // Mise à jour du minimum
      }
    }
  }

  peakToPeak = signalMax - signalMin; // Calcul de l'amplitude crête-à-crête
  return peakToPeak;
}
