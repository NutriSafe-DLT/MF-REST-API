package de.nutrisafe.converter;

import de.nutrisafe.Utils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import java.util.ArrayList;
import java.util.HashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JsonConverter {

    JSONObject metaDef = new JSONObject();
    JSONObject bordDef = new JSONObject();
    JSONObject statDef = new JSONObject();
    JSONArray datasets = new JSONArray();
    ArrayList<String> sendPosNames = new ArrayList<>();
    String bordMessage;
    String statMessage;
    String id;
    int sendPosAmount = 0;
    Utils helper;
    char[][] bordMessageChar = new char[30][200];
    char[][] statMessageChar = new char[30][200];

    public JsonConverter(){
        for(int i = 0; i < 30; i++)
        {
            for(int j = 0; j < 200; j++)
            {
                bordMessageChar[i][j] = ' ';
                statMessageChar[i][j] = ' ';
            }
        }
    }

    public String uploadBordMessage(String id, String body, Utils helper) throws Exception{
        this.id = id;
        this.helper = helper;
        bordMessage = body;
        metaDef = new JSONObject(helper.evaluateTransaction("META_readMetaDef", null)).getJSONObject("response").getJSONObject("shipmentNameToAttributesMap");
        System.out.println("MetaDef: " + metaDef);
        bordDef = new JSONObject(IOUtils.toString(new ClassPathResource("BORD_def.json").getInputStream(), UTF_8));
        System.out.println("BordDef: " + bordDef);
        datasets = bordDef.getJSONObject("structure").getJSONArray("datasets");
        System.out.println("Datasets: " + datasets);

        toCharArray();
        uploadLadung();
        uploadSendPos();
        uploadSendung();

        return "ok";
    }

    public String uploadStatMessage(String id, String body, Utils helper) throws Exception{
        this.id = id;
        this.helper = helper;
        this.statMessage = body;
        metaDef = new JSONObject(helper.evaluateTransaction("META_readMetaDef", null)).getJSONObject("response").getJSONObject("shipmentNameToAttributesMap");
        statDef = new JSONObject(IOUtils.toString(new ClassPathResource("STAT_def.json").getInputStream(), UTF_8));
        datasets = statDef.getJSONObject("structure").getJSONArray("datasets");

        statToCharArray();
        System.out.println("hier:");
        printArray(statMessageChar);
        uploadstatusmeldungBarcode();

        return "ok";

    }

    void uploadstatusmeldungBarcode(){
        JSONArray statBarcodeDef = metaDef.getJSONArray("Statusmeldung_Barcode");

        ArrayList<String> arrayAttributes = new ArrayList<>();
        ArrayList<ArrayList<String>> arrayValues = new ArrayList<>();

        int barcodeAmount = 0;

        for (char[] line : statMessageChar){
            if ('Q' == line[0] && '2' == line[1]){
                barcodeAmount++;
            }
        }

        for (int x = 0; x < statBarcodeDef.length(); x++){
            String searchFor = statBarcodeDef.getString(x);

            JSONObject rowDef = statDef.getJSONObject("Q20");           //Definition der D00 Reihe
            JSONArray keys = rowDef.names();

            for (int z = 0; z < keys.length(); z++) {
                if (keys.getString(z).equals(searchFor)) {
                    arrayAttributes.add(keys.getString(z));
                    JSONArray startEnd = statDef.getJSONObject("Q20").getJSONArray(keys.getString(z));
                    ArrayList<String> temp = new ArrayList<>();
                    temp.add(statGetStringfromPosition("Q20", startEnd.getInt(0), startEnd.getInt(1)).trim());
                    arrayValues.add(temp);
                }
            }
        }

        System.out.println(arrayAttributes);
        System.out.println(arrayValues);

        statDeleteRow("G20");

        ArrayList<String> toPass = new ArrayList<>();
        toPass.add(id + "_STATUSMELDUNG_BARCODE");
        toPass.add("Statusmeldung_Barcode");
        toPass.add("[]");
        toPass.add("[]");
        toPass.add(arrayAttributes.toString());
        toPass.add(arrayValues.toString().replaceAll("\\s+",""));
        System.out.println(helper.submitTransaction("createShipment", toPass.toArray(new String[toPass.size()]), new HashMap<>()));

        toPass.clear();
        toPass.add(id + "_SENDUNG");
        toPass.add("Statusmeldungen_Barcode");
        toPass.add(id + "_STATUSMELDUNG_BARCODE");
        toPass.add("ADD_NEW");
        System.out.println("Updating Array...");
        System.out.println(helper.submitTransaction("updateArray", toPass.toArray(new String[toPass.size()]), new HashMap<>()));




    }

    private void toCharArray(){
        String[] temp = bordMessage.split("\\r?\\n");
        int i = 0;
        for (String line : temp){
            bordMessageChar[i] = line.toCharArray();
            i++;
        }
    }

    private void statToCharArray(){
        String[] temp = statMessage.split("\\r?\\n");
        int i = 0;
        for (String line : temp){
            statMessageChar[i] = line.toCharArray();
            i++;
        }
    }

    void printArray(char[][] toPrint){
        System.out.println();
        for(int i = 0; i < 30; i++)
        {
            for(int j = 0; j < 200; j++)
            {
                System.out.print(toPrint[i][j]);
            }
            System.out.println();
        }
    }

    private void uploadLadung(){
        JSONArray ladungDef = metaDef.getJSONArray("Ladung");
        System.out.println("LadungDef: " + ladungDef);

        ArrayList<String> attrNames = new ArrayList<>();
        ArrayList<String> attrValues = new ArrayList<>();

        for (int x = 0; x < ladungDef.length(); x++){
            String searchFor = ladungDef.getString(x);
            for (int y = 0; y < datasets.length(); y++){
                JSONObject rowDef = bordDef.getJSONObject(datasets.getString(y));
                JSONArray keys = rowDef.names();
                for (int z = 0; z < keys.length(); z++) {
                    if (keys.getString(z).equals(searchFor)){
                        attrNames.add(keys.getString(z));
                        JSONArray startEnd =  bordDef.getJSONObject(datasets.getString(y)).getJSONArray(keys.getString(z));
                        attrValues.add(getStringfromPosition(datasets.getString(y), startEnd.getInt(0), startEnd.getInt(1)).trim());
                    }
                }

            }
        }
        System.out.println(attrNames);
        System.out.println(attrValues);

        ArrayList<String> toPass = new ArrayList<>();
        toPass.add(id + "_LADUNG");
        toPass.add("Ladung");
        toPass.add(attrNames.toString());
        toPass.add(attrValues.toString());
        toPass.add("[]");
        toPass.add("[[]]");

        System.out.println("toUpload: " + toPass);

        System.out.println(helper.submitTransaction("createShipment", toPass.toArray(new String[toPass.size()]), new HashMap<>()));

    }

    private void uploadSendung(){
        JSONArray sendungDef = metaDef.getJSONArray("Sendung");
        System.out.println("SendungDef: " + sendungDef);

        ArrayList<String> attrNames = new ArrayList<>();
        ArrayList<String> attrValues = new ArrayList<>();
        ArrayList<String> arrayAttributes = new ArrayList<>();
        ArrayList<ArrayList<String>> arrayValues = new ArrayList<>();
        boolean rowDeleted = false;
        for (int x = 0; x < sendungDef.length(); x++){
            String searchFor = sendungDef.getString(x);
            if (searchFor.equals("Empfänger_Name") && !rowDeleted){
                System.out.println("Deleting...");
                deleteRow("B00");
                rowDeleted = true;
            }
            for (int y = 0; y < datasets.length(); y++){
                JSONObject rowDef = bordDef.getJSONObject(datasets.getString(y));

                JSONArray keys = rowDef.names();
                for (int z = 0; z < keys.length(); z++) {
                    if (keys.getString(z).equals(searchFor)){
                        attrNames.add(keys.getString(z));
                        JSONArray startEnd =  bordDef.getJSONObject(datasets.getString(y)).getJSONArray(keys.getString(z));
                        attrValues.add(getStringfromPosition(datasets.getString(y), startEnd.getInt(0), startEnd.getInt(1)).trim());
                    }
                }
            }
        }
        arrayAttributes.add("Sendungspositonen");                   //TYPO!!!
        arrayValues.add(sendPosNames);


        System.out.println(attrNames);
        System.out.println(attrValues);
        System.out.println(arrayAttributes);
        System.out.println(arrayValues);

        ArrayList<String> toPass = new ArrayList<>();
        toPass.add(id + "_SENDUNG");
        toPass.add("Sendung");
        toPass.add(attrNames.toString());
        toPass.add(attrValues.toString());
        toPass.add(arrayAttributes.toString());
        toPass.add(arrayValues.toString().replaceAll("\\s+",""));

        System.out.println("toUpload: " + toPass);

        System.out.println(helper.submitTransaction("createShipment", toPass.toArray(new String[toPass.size()]), new HashMap<>()));

    }

    private void uploadSendPos() {
        JSONArray sendPosDef = metaDef.getJSONArray("Sendungspositon");         //TYPO!!!
        System.out.println("SendposDef: " + sendPosDef);

        for (char[] line : bordMessageChar){
            if ('D' == line[0] && '0' == line[1]){
                sendPosAmount++;
            }
        }
        int actualSendPos = 1;
        for (int actualSendPosIteration = 0; actualSendPosIteration < sendPosAmount; actualSendPosIteration++) {

            ArrayList<String> attrNames = new ArrayList<>();
            ArrayList<String> attrValues = new ArrayList<>();
            ArrayList<String> arrayAttributes = new ArrayList<>();
            ArrayList<ArrayList<String>> arrayValues = new ArrayList<>();

            for (int x = 0; x < sendPosDef.length(); x++) {                 //Iteration über die in der MetaDef hinterlegten Attribute
                String searchFor = sendPosDef.getString(x);                 //Das jeweilige Attribute nach dem gesucht wird
                JSONObject rowDef = bordDef.getJSONObject("D00");           //Definition der D00 Reihe
                JSONArray keys = rowDef.names();                            //Die einzelnen Namen der Attribute der D00 Reihe wie in BORD_DEF
                for (int z = 0; z < keys.length(); z++) {
                    if (keys.getString(z).equals(searchFor)) {               //Wenn das Attribut in der MetaDef auftaucht
                        attrNames.add(keys.getString(z));
                        JSONArray startEnd = bordDef.getJSONObject("D00").getJSONArray(keys.getString(z));
                        attrValues.add(getStringfromPosition("D00", startEnd.getInt(0), startEnd.getInt(1)).trim());
                    }
                }
            }
            JSONArray startEnd = bordDef.getJSONObject("F00").getJSONArray("Barcodes");
            arrayAttributes.add("Barcodes");
            ArrayList<String> temp = new ArrayList<>();
            temp.add(getStringfromPosition("F00", startEnd.getInt(0), startEnd.getInt(1)).trim());
            arrayValues.add(temp);

            System.out.println(attrNames);
            System.out.println(attrValues);
            System.out.println(arrayAttributes);
            System.out.println(arrayValues);

            ArrayList<String> toPass = new ArrayList<>();
            toPass.add(id + "_SENDPOS" + actualSendPos);
            sendPosNames.add(id + "_SENDPOS" + actualSendPos);
            toPass.add("Sendungspositon");              //TYPO!!!
            toPass.add(attrNames.toString());
            toPass.add(attrValues.toString());
            toPass.add(arrayAttributes.toString());
            toPass.add(arrayValues.toString());

            System.out.println("toUpload: " + toPass);
            deleteRow("D00");
            deleteRow("F00");

            System.out.println(helper.submitTransaction("createShipment", toPass.toArray(new String[toPass.size()]), new HashMap<>()));
            actualSendPos++;
        }
        System.out.println("SendPosNames: " + sendPosNames);
    }



    void deleteRow(String row){
        System.out.println("Deleting row: " + row);
        char[] rowChar = row.toCharArray();
        boolean foundRow = false;
        int index = -1;
        for (char[] line : bordMessageChar){
            if (!foundRow) {
                if (rowChar[0] == line[0] && rowChar[1] == line[1] && rowChar[2] == line[2]) {
                    foundRow = true;
                }
                index++;
            }
        }
        char[] toFill = new char[200];
        for (int i = 0; i < 200; i++){
            toFill[i] = ' ';
        }
        bordMessageChar[index] = toFill;
        printArray(bordMessageChar);
    }

    void statDeleteRow(String row){
        char[] rowChar = row.toCharArray();
        boolean foundRow = false;
        int index = -1;
        for (char[] line : statMessageChar){
            if (!foundRow) {
                if (rowChar[0] == line[0] && rowChar[1] == line[1] && rowChar[2] == line[2]) {
                    foundRow = true;
                }
                index++;
            }
        }
        char[] toFill = new char[200];
        for (int i = 0; i < 200; i++){
            toFill[i] = ' ';
        }
        statMessageChar[index] = toFill;
    }

    String getStringfromPosition(String row, int startIndex, int endIndex){
        System.out.println("Finding in row: " + row + " at start: " + startIndex + " end: " + endIndex);
        char[] rowChar = row.toCharArray();
        String result = "";
        boolean foundRow = false;
        for (char[] line : bordMessageChar){
            if (!foundRow) {
                if (rowChar[0] == line[0] && rowChar[1] == line[1] && rowChar[2] == line[2]) {
                    foundRow = true;
                    for (int i = startIndex - 1; i < endIndex; i++) {
                        result = result + line[i];
                    }
                }
            }
        }
        System.out.println(result);
        return result;
    }

    String statGetStringfromPosition(String row, int startIndex, int endIndex){
        System.out.println("Finding in row: " + row + " at start: " + startIndex + " end: " + endIndex);
        char[] rowChar = row.toCharArray();
        String result = "";
        boolean foundRow = false;
        for (char[] line : statMessageChar){
            if (!foundRow) {
                if (rowChar[0] == line[0] && rowChar[1] == line[1] && rowChar[2] == line[2]) {
                    foundRow = true;
                    for (int i = startIndex - 1; i < endIndex; i++) {
                        result = result + line[i];
                    }
                }
            }
        }
        System.out.println(result);
        return result;
    }

}
