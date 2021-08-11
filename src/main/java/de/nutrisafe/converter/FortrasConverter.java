package de.nutrisafe.converter;

import de.nutrisafe.Utils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;

public class FortrasConverter {

    JSONObject all = new JSONObject();
    JSONObject fortrasDef = new JSONObject();
    JSONObject rowStructure = new JSONObject();
    JSONObject structure = new JSONObject();
    char[][] result = new char[30][200];
    int sendPosAmount = 0;
    int actualRow = 0;
    int actualSendPos = 0;
    int actualIteration = 0;
    int actualStatPos = 0;
    String id = "";

    public FortrasConverter(){
        for(int i = 0; i < 30; i++)
        {
            for(int j = 0; j < 200; j++)
            {
                result[i][j] = ' ';
            }
        }
    }

    public String convertToBordMessage(String[] args, Utils helper){

        all =  concatObjects(args, helper, false);

        try {
            ClassPathResource classPathResource = new ClassPathResource("BORD_def.json");
            InputStream inputStream = classPathResource.getInputStream();
            fortrasDef = new JSONObject(IOUtils.toString(inputStream, UTF_8));
            System.out.println(fortrasDef);
        }
        catch (Exception e){
            System.out.println(e);
        }

        insertString("@@PHBORD512 0512003500107 DE370   ELVIS", actualRow, 0);
        actualRow++;

        structure = fortrasDef.getJSONObject("structure");

        for (int x = 1; x <= structure.getInt("amount"); x++){
            actualIteration = x;

            rowStructure = structure.getJSONObject(String.valueOf(x));

            if (rowStructure.getString("type").equals("normal")){
                insertNormalRow();
            }
            else if (rowStructure.getString("type").equals("followed")){
                insertFollowedRow();
            }
            else if (rowStructure.getString("type").equals("complexVertical")){
                insertComplexVerRow();
            }
        }

        insertString("@@PT", actualRow, 0);
        printArray();
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < result.length; i++){
            builder.append(result[i]);
            builder.append("\n");
        }

        return builder.toString();

    }

    public String convertToStatMessage(String[] args, Utils helper){

        all =  concatObjects(args, helper, true);

        System.out.println(all);

        try {
            ClassPathResource classPathResource = new ClassPathResource("STAT_def.json");
            InputStream inputStream = classPathResource.getInputStream();
            fortrasDef = new JSONObject(IOUtils.toString(inputStream, UTF_8));
            System.out.println(fortrasDef);
        }
        catch (Exception e){
            System.out.println(e);
        }

        insertString("@@PHSTAT512 0512  35  1 5 DE564   ELVIS", actualRow, 0);
        actualRow++;

        structure = fortrasDef.getJSONObject("structure");

        for (int x = 1; x <= structure.getInt("amount"); x++){
            actualIteration = x;

            rowStructure = structure.getJSONObject(String.valueOf(x));

            if (rowStructure.getString("type").equals("normal")){
                insertNormalRow();
            }
            else if (rowStructure.getString("type").equals("complexVertical")){
                if (rowStructure.getString("row").equals("Q10")){
                    insertComplexVerRowStat("STATUSMELDUNG_SENDUNG_");
                }
                else {
                    insertComplexVerRowStat("STATUSMELDUNG_BARCODE_");
                }

            }
        }

        insertString("@@PT", actualRow, 0);
        printArray();
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < result.length; i++){
            builder.append(result[i]);
            builder.append("\n");
        }

        return builder.toString();


    }

    void printArray(){
        System.out.println();
        for(int i = 0; i < result.length; i++)
        {
            for(int j = 0; j < 200; j++)
            {
                System.out.print(result[i][j]);
            }
            System.out.println();
        }
    }

    void insertComplexVerRowStat(String prefix){

        System.out.println("Checking...");
        if (rowStructure.getBoolean("optional")){
            String dependsOnString = prefix +  "0_" + rowStructure.getString("dependsOn");
            System.out.println("DependsString: " + dependsOnString);
            if (!all.getJSONObject("attributes").has(dependsOnString)){
                return;
            }
        }
        System.out.println("Ja!");

        JSONObject rowDef = fortrasDef.getJSONObject(rowStructure.getString("row"));
        JSONArray keys = rowDef.names();

        JSONArray iteratingArray = all.getJSONObject("attributes").getJSONArray(prefix + actualStatPos + "_" + rowStructure.getString("dependsOn"));

        for(int x = 0; x < iteratingArray.length(); x++){
            for (int i = 0; i < keys.length(); i++) {
                JSONArray startEnd = rowDef.getJSONArray(keys.getString(i));
                System.out.println("Ckecing for: " + prefix + actualStatPos + "_" + keys.getString(i));
                    if (all.getJSONObject("attributes").has(prefix + actualStatPos + "_" + keys.getString(i))) {
                        System.out.println(prefix + actualStatPos + "_" + keys.getString(i));

                        System.out.println("try");
                        JSONArray test = all.getJSONObject("attributes").getJSONArray(prefix + actualStatPos + "_" + keys.getString(i));
                        System.out.println(test.get(0));
                        insertString(test.getString(x), actualRow, startEnd.getInt(0) - 1);

                    }
                    else if (all.getJSONObject("attributes").has(keys.getString(i))){
                        insertString(all.getJSONObject("attributes").getString(keys.getString(i)), actualRow, startEnd.getInt(0) - 1);
                    }
                    else if (startEnd.length() > 2) {
                        insertString(startEnd.getString(2), actualRow, startEnd.getInt(0) - 1);
                    }
                }
            actualRow++;
            actualStatPos++;
        }
        actualStatPos = 0;

    }

    void insertFollowedRow(){
        System.out.println("Insert Follow Row");
        rowStructure = structure.getJSONObject(String.valueOf(actualIteration));
        JSONObject rowDef = fortrasDef.getJSONObject(rowStructure.getString("row"));
        JSONArray keys = rowDef.names();
        for (int i = 0; i < keys.length(); i++) {
            JSONArray startEnd = rowDef.getJSONArray(keys.getString(i));
            if (all.getJSONObject("attributes").has(id + "_SENDPOS" + actualSendPos + "_" + keys.getString(i))) {                //Key aus der Def ist im concat Objekt vorhanden
                insertString(all.getJSONObject("attributes").getString(id + "_SENDPOS" + actualSendPos + "_"  + keys.getString(i)), actualRow, startEnd.getInt(0) - 1);
            }
            else if (startEnd.length() > 2) {                                //Check for default value
                if (keys.getString(i).equals("Sendungsposition")){
                    insertString("00" + (actualSendPos + 1), actualRow, startEnd.getInt(0) - 1);
                }
                else {
                    insertString(startEnd.getString(2), actualRow, startEnd.getInt(0) - 1);
                }
            }
        }

        actualRow++;

        JSONArray followedBy = rowStructure.getJSONArray("followedBy");

        for (int i = 0; i < followedBy.length(); i++) {
            rowStructure = structure.getJSONObject(followedBy.getString(i));
            if (rowStructure.getString("type").equals("normal")){
                insertNormalRow();
            }
            else if (rowStructure.getString("type").equals("complexVertical")){
                insertComplexVerRow();
            }

        }
        actualSendPos++;

        if (actualSendPos < sendPosAmount) {
            insertFollowedRow();
        }


    }

    void insertComplexVerRow(){
        System.out.println("Checking if complex ver row is optional...");
        if (rowStructure.getBoolean("optional")){
            String dependsOnString =id +  "_SENDPOS" + actualSendPos + "_" + rowStructure.getString("dependsOn");
            System.out.println("DependsString: " + dependsOnString);
            if (!all.getJSONObject("attributes").has(dependsOnString)){
                return;
            }
        }

        System.out.println("Insert Complex Vertical Row");

        JSONObject rowDef = fortrasDef.getJSONObject(rowStructure.getString("row"));
        JSONArray keys = rowDef.names();
        JSONArray iteratingArray = all.getJSONObject("attributes").getJSONArray(id + "_SENDPOS" + actualSendPos + "_" + rowStructure.getString("dependsOn"));

        for(int x = 0; x < iteratingArray.length(); x++){
            for (int i = 0; i < keys.length(); i++) {
                JSONArray startEnd = rowDef.getJSONArray(keys.getString(i));
                if (all.getJSONObject("attributes").has(id + "_SENDPOS" + actualSendPos + "_" + keys.getString(i))) {                //Key aus der Def ist im concat Objekt vorhanden
                    System.out.println("Key: " + keys.getString(i));
                    if (!keys.getString(i).equals(rowStructure.getString("dependsOn"))) {
                        insertString(all.getJSONObject("attributes").getString(keys.getString(i)), actualRow, startEnd.getInt(0) - 1);
                    }
                    else {
                        System.out.println("barcode");
                        insertString(iteratingArray.getString(x), actualRow, startEnd.getInt(0) - 1);
                    }
                }
                else if (startEnd.length() > 2) {                                //Check for default value
                    if (keys.getString(i).equals("Sendungsposition")){
                        insertString("00" + (actualSendPos + 1), actualRow, startEnd.getInt(0) - 1);
                    }
                    else {
                        insertString(startEnd.getString(2), actualRow, startEnd.getInt(0) - 1);
                    }
                }
            }
            actualRow++;
        }
    }

    void insertNormalRow(){

        if (rowStructure.getBoolean("optional")){
            String dependsOnString = rowStructure.getString("dependsOn");
            System.out.println("Checking for: " + dependsOnString);
            if (!all.getJSONObject("attributes").has(dependsOnString)){
                System.out.println("Skip row");
                return;
            }
        }
        System.out.println("Insert Normal Row");
        JSONObject rowDef = fortrasDef.getJSONObject(rowStructure.getString("row"));
        JSONArray keys = rowDef.names();
        for (int i = 0; i < keys.length(); i++) {
            JSONArray startEnd = rowDef.getJSONArray(keys.getString(i));
            if (all.getJSONObject("attributes").has(keys.getString(i))) {                //Key aus der Def ist im concat Objekt vorhanden
                insertString(all.getJSONObject("attributes").getString(keys.getString(i)), actualRow, startEnd.getInt(0) - 1);
            }
            else if (all.getJSONObject("attributes").has("STATUSMELDUNG_SENDUNG_0_"  + keys.getString(i))){
                insertString(all.getJSONObject("attributes").getString("STATUSMELDUNG_SENDUNG_0_" + keys.getString(i)), actualRow, startEnd.getInt(0) - 1);
            }
            else if (startEnd.length() > 2) {                                //Check for default value
                insertString(startEnd.getString(2), actualRow, startEnd.getInt(0) - 1);
            }
        }
        actualRow++;


    }

    void insertString( String toInsert, int rowIndex, int start){
        char[] toInsertChar = toInsert.toCharArray();
        for (int i = 0; i < toInsert.length(); i++){
            result[rowIndex][start] = toInsertChar[i];
            start++;
        }
    }

    public JSONObject concatObjects(String[] args, Utils helper, boolean needAll){

        JSONObject all = new JSONObject();
        JSONObject response = new JSONObject();

        id = args[0];
        args[0] = id + "_LADUNG";
        try {
            response = new JSONObject(helper.evaluateTransaction("readShipment", args));
        }
        catch (Exception e){
            System.out.println("Fehler in Block 1: " + e);
        }

        JSONObject attributes = response.getJSONObject("response").getJSONObject("attributes");
        JSONObject arrays = response.getJSONObject("response").getJSONObject("attributeArrays");

        if (arrays.length() > 1) {
            for (String key : JSONObject.getNames(arrays)) {
                attributes.put(key, arrays.get(key));
            }
        }


        args[0] = id + "_SENDUNG";
        try {
            response = new JSONObject(helper.evaluateTransaction("readShipment", args));
        }
        catch (Exception e){
            System.out.println("Fehler in Block 2: " + e);
        }

        JSONObject attributes1 = response.getJSONObject("response").getJSONObject("attributes");
        arrays = response.getJSONObject("response").getJSONObject("attributeArrays");

        for (String key : JSONObject.getNames(arrays)){
            attributes.put(key, arrays.get(key));
        }

        for (String key : JSONObject.getNames(attributes1)){
            attributes.put(key, attributes1.get(key));
        }
        JSONArray sendPos = attributes.getJSONArray("Sendungspositonen");           //TYPO!!!
        sendPosAmount = sendPos.length();
        for (int i = 0; i < sendPos.length(); i++) {
            args[0] = (String) sendPos.get(i);
            try {
                response = new JSONObject(helper.evaluateTransaction("readShipment", args)).getJSONObject("response");
            }
            catch (Exception e){
                System.out.println("Fehler in Block 3: " + e);
            }
            JSONObject temp = response.getJSONObject("attributes");

            for (String key : JSONObject.getNames(temp)){
                attributes.put(id + "_SENDPOS" + i + "_" + key, temp.get(key));
            }

            temp = response.getJSONObject("attributeArrays");
            for (String key : JSONObject.getNames(temp)){
                attributes.put(id + "_SENDPOS" + i + "_" + key, temp.get(key));
            }
        }

        if(needAll) {
            JSONArray statBarcode = attributes.getJSONArray("Statusmeldungen_Barcode");
            for (int i = 0; i < statBarcode.length(); i++) {
                args[0] = (String) statBarcode.get(i);
                try {
                    response = new JSONObject(helper.evaluateTransaction("readShipment", args)).getJSONObject("response");
                } catch (Exception e) {
                    System.out.println("Fehler in Block 3: " + e);
                }
                JSONObject temp = response.getJSONObject("attributes");

                if (temp.length() > 0) {
                    for (String key : JSONObject.getNames(temp)) {
                        attributes.put("STATUSMELDUNG_BARCODE_" + i + "_" + key, temp.get(key));
                    }
                }

                temp = response.getJSONObject("attributeArrays");
                for (String key : JSONObject.getNames(temp)) {
                    attributes.put("STATUSMELDUNG_BARCODE_" + i + "_" + key, temp.get(key));
                }
            }
            if (attributes.has("Statusmeldungen_Sendung")) {
                JSONArray statSendung = attributes.getJSONArray("Statusmeldungen_Sendung");
                for (int i = 0; i < statSendung.length(); i++) {
                    args[0] = (String) statSendung.get(i);
                    try {
                        response = new JSONObject(helper.evaluateTransaction("readShipment", args)).getJSONObject("response");
                    } catch (Exception e) {
                        System.out.println("Fehler in Block 3: " + e);
                    }
                    JSONObject temp = response.getJSONObject("attributes");

                    for (String key : JSONObject.getNames(temp)) {
                        attributes.put("STATUSMELDUNG_SENDUNG_" + i + "_" + key, temp.get(key));
                    }

                    temp = response.getJSONObject("attributeArrays");
                    for (String key : JSONObject.getNames(temp)) {
                        attributes.put("STATUSMELDUNG_SENDUNG_" + i + "_" + key, temp.get(key));
                    }
                }
            }
        }


        all.put("attributes", attributes);

        System.out.println(all);

        return all;
    }

}
