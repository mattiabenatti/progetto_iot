#include <WiFi.h>
#include <PubSubClient.h>
#include <SPI.h>
#include <SD.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include "DHT.h"
#define DHTTYPE DHT22
#define DHTPIN 4
#define SD_CS_PIN 5
#define DESCRIPTOR_UUID "00002902-0000-1000-8000-00805f9b34fb"
#define SERVICE_UUID        "00001809-0000-1000-8000-00805F9B34FB"
#define CHARACTERISTIC_UUID "00002A1C-0000-1000-8000-00805F9B34FB"
#define CHARACTERISTIC_UUID_2 "f4eaba63-3a74-4a99-b932-2a4ea823bbe4"
#define PATH "/dataTemp.txt"
#define ATTEMPT_MAX 3
#define LED_GREEN 25
#define LED_YELLOW 26
#define LED_BLUE 27

BLECharacteristic *pCharacteristic;
BLECharacteristic *pCharacteristic2;
int cont;
boolean wrote;
boolean request;
boolean wait;
DHT dht(DHTPIN, DHTTYPE);
//const char* ssid       = "Alice-64857913";
//const char* password   = "0m5nu1bkgzzefazq88oulp4y";
//const char* password   = "26746166";
//const char* ssid       = "TP-LINK_379614";
const char* ssid       = "Honor9";
const char* password   = "ciaociao";
const char* clientId="esp32Mattia";
const char* topic="esp32Mattia/Temperature";
boolean published=true;
float noData;
float yData;
float otherData;
char valueString[20];
char inputChar;
int indexChar;
float valueTemp;
boolean p;
boolean sendOther;
int attemptDone;
File file;
//IPAddress ip(192, 168, 1, 134);
//IPAddress ip(192, 168, 1, 107);
//IPAddress ip(192, 168, 43, 20);
const char* mqttServer="test.mosquitto.org";

WiFiClient wifiClient;
PubSubClient client;
class MyCallbacks2: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string value = pCharacteristic->getValue();

      if(value=="req"){
        if (!sendOther){
          request=true;
          Serial.println("req");
          digitalWrite(LED_BLUE, HIGH);
          file=SD.open(PATH);
        }
          if (file){
            while (file.available()){
              if (cont==50){
                sendOther=true;
                cont=0;
                pCharacteristic->setValue(otherData);
                pCharacteristic->notify();
                break;
                
                
              }
              else{
                inputChar=file.read();
                if (inputChar!='\n'){
                  valueString[indexChar]=inputChar;
                  indexChar=indexChar+1;
                }
                else{
                  valueString[indexChar]='\0';
                  Serial.print("valueString: ");
                  Serial.println(valueString);
                  valueTemp=atof(valueString);
                  pCharacteristic2->setValue(valueTemp);
                  pCharacteristic2->notify();
                  indexChar=0;
                  cont=cont+1;
                }
              }
              
            }
            if(!file.available()){
              sendOther=false;
              cont=0;
              pCharacteristic2->setValue(noData);
              pCharacteristic2->notify();
            }
            
            
          }
      }
      else if (value=="acc"){
          Serial.println("ACC");
          file.close();
          SD.remove(PATH);
          Serial.println("REMOVED");
          
          pCharacteristic->setValue(noData);
          pCharacteristic->notify();  
          digitalWrite(LED_BLUE, LOW);
          published=true;
          request=false;
          wrote=true;
         
      }
      else if (value=="ok"){
        /*Serial.println("sendOther");
        if (file){
            while (file.available()){
              if (cont==30){
                pCharacteristic->setValue(otherData);
                pCharacteristic->notify();
                while (!sendOther){
                  delay(10);
                }
                sendOther=false;
                cont=0;
              }
              else{
                inputChar=file.read();
                if (inputChar!='\n'){
                  valueString[indexChar]=inputChar;
                  indexChar=indexChar+1;
                }
                else{
                  valueString[indexChar]='\0';
                  Serial.print("valueString: ");
                  Serial.println(valueString);
                  valueTemp=atof(valueString);
                  pCharacteristic2->setValue(valueTemp);
                  pCharacteristic2->notify();
                  indexChar=0;
                  cont=cont+1;
                }
              }
              
            }
            pCharacteristic2->setValue(noData);
            pCharacteristic2->notify();
            
          }
      }*/
      }
      else {
        Serial.println("acc OK");
        wrote=true;
      }
      
    }
};
boolean publishData(const char* myID, const char *topic, float temp){
  if(WiFi.status()!=WL_CONNECTED){
    return false;
  }
  boolean connectedMqtt=client.connected();
  if (!connectedMqtt){
    Serial.println("nope");
    connectedMqtt=client.connect(myID);
  }
  if (connectedMqtt){
    Serial.println("connesso");
    char t[10];
    sprintf(t,"%f",temp);
    boolean result=client.publish(topic,t);
    Serial.print("pubblicamento di: ");
    Serial.println(t);
    Serial.println(result);
    //client.loop();
    return result;
  }
  else{
    Serial.println("non connesso");
    return false;
  }
}
void setup() {
  // put your setup code here, to run once:
  Serial.begin(115200);
  wrote=false;
  request=false;
  sendOther=false;
  noData=0.02;
  yData=0.01;
  otherData=0.03;
  cont=0;
  wait=false;
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_YELLOW, OUTPUT);
  pinMode(LED_BLUE, OUTPUT);
  attemptDone=0;
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED && attemptDone<ATTEMPT_MAX) {
      delay(500);
      attemptDone=attemptDone+1;
  }
  attemptDone=0;
  if (WiFi.status()!=WL_CONNECTED){
    digitalWrite(LED_YELLOW, HIGH);
  }
  else{
    digitalWrite(LED_GREEN, HIGH);
  }
  Serial.println("connected");
  client.setServer(mqttServer, 1883);
  client.setClient(wifiClient);
  if (!SD.begin(SD_CS_PIN)){
    return;
  }
  if (SD.exists("/dataTemp.txt")){
    Serial.print("REMOVED setup ");
    Serial.println(SD.remove(PATH));
  }
  BLEDevice::init("esp32temp");
  BLEServer *pServer = BLEDevice::createServer();
  BLEService *pService = pServer->createService(SERVICE_UUID);
  pCharacteristic= pService->createCharacteristic(
                                         CHARACTERISTIC_UUID,
                                         BLECharacteristic::PROPERTY_READ|
                                         BLECharacteristic::PROPERTY_NOTIFY
                                       );

  pCharacteristic2= pService->createCharacteristic(
                                         CHARACTERISTIC_UUID_2,
                                         BLECharacteristic::PROPERTY_READ|
                                         BLECharacteristic::PROPERTY_WRITE|
                                         BLECharacteristic::PROPERTY_NOTIFY
                                       );
  pCharacteristic2->setCallbacks(new MyCallbacks2());
  pCharacteristic2->setValue(noData);
  pService->start();
  //pCharacteristic->addDescriptor(DESCRIPTOR_UUID,BLERead);
  // BLEAdvertising *pAdvertising = pServer->getAdvertising();  // this still is working for backward compatibility
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // functions that help with iPhone connections issue
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  
  dht.begin();
  
}

void loop() {
  // put your main code here, to run repeatedly:
  delay(2000);
  float t=dht.readTemperature();
  if (!isnan(t)){
    if (!request){
    Serial.print("STATO WIFI: ");
    Serial.println(WiFi.status()==WL_CONNECTED);
    if (WiFi.status()!=WL_CONNECTED){
      digitalWrite(LED_GREEN, LOW);
      digitalWrite(LED_YELLOW, HIGH);
      WiFi.begin(ssid, password);
      client.setServer(mqttServer, 1883);
      client.setClient(wifiClient);
    }
    if (WiFi.status()==WL_CONNECTED){
      digitalWrite(LED_YELLOW, LOW);
      digitalWrite(LED_GREEN, HIGH);
    }
    if((WiFi.status()==WL_CONNECTED) && !published){
      Serial.println("reconnected");
      file=SD.open(PATH,FILE_READ);
      if (file){
        Serial.println("file esistente");
        while (file.available()){
          inputChar=file.read();
          if (inputChar!='\n'){
            valueString[indexChar]=inputChar;
            indexChar=indexChar+1;
          }
          else{
            valueString[indexChar]='\0';
            valueTemp=atof(valueString);
            Serial.print("REpublishing: ");
            Serial.println(valueString);
            p=publishData(clientId,topic,valueTemp);
            while (!p && attemptDone<ATTEMPT_MAX){
              p=publishData(clientId,topic,valueTemp);
              attemptDone=attemptDone+1;
            }
            
           indexChar=0;
           attemptDone=0;
          }
        }
        file.close();
        SD.remove(PATH);
        published=true;
      }
    }
    
    if(published){
      published=publishData(clientId,topic,t);
    }
    if (!published){
      if (!SD.exists(PATH)){
        file=SD.open(PATH,FILE_WRITE);
      }
      //salva su sd perchè gli ultimi dati non sono stati pubblicati
      else{
         file=SD.open(PATH,FILE_APPEND);
  
      }
      boolean a=file.println(t);
      Serial.print("SCRITTO: ");
      Serial.println(a);
      Serial.print("SD: ");
      Serial.println(t);
      file.close();
      pCharacteristic2->setValue(yData);
      pCharacteristic2->notify();
      
    }
    else{
      Serial.print("Published, temp= ");
      Serial.println(t);
      pCharacteristic2->setValue(noData);
      pCharacteristic2->notify();
    }
    pCharacteristic->setValue(t);
    pCharacteristic->notify();
    }
  }

}
