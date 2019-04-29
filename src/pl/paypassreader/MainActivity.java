package pl.paypassreader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import pl.paypassreader.R;

import sasc.emv.ApplicationElementaryFile;
import sasc.emv.DOL;
import sasc.emv.EMVAPDUCommands;
import sasc.emv.EMVApplication;
import sasc.emv.EMVUtil;
import sasc.emv.PAN;
import sasc.iso7816.Iso7816Commands;
import sasc.terminal.CardResponse;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TableRow;
import android.widget.TableRow.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {
// Dialogs
private static final int DIALOG_NFC_NOT_AVAIL = 0;

private static final String LOGTAG = "ECInfoGrabber";

private NfcAdapter nfc;
private Tag tag;
public static IsoDep tagcomm;
private String[][] nfctechfilter = new String[][] { new String[] { NfcA.class.getName() } };
private PendingIntent nfcintent;
private byte AID[];

private TextView nfcid;
private TextView cardtype;
private TextView kknr;
private TextView verfall;
private TextView displayaid;
private TextView owner;
private TextView rawdata;

/** Called when the activity is first created. */
@Override
public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.glowne);
    
	nfcid = (TextView) findViewById(R.id.display_nfcid);
	cardtype = (TextView) findViewById(R.id.display_cardtype);
	kknr = (TextView) findViewById(R.id.display_kknr);
	verfall = (TextView) findViewById(R.id.display_verfall);
	rawdata = (TextView) findViewById(R.id.raw_data);
	owner = (TextView) findViewById(R.id.display_owner);
	displayaid = (TextView) findViewById(R.id.display_aid);
	
    
    nfc = NfcAdapter.getDefaultAdapter(this);
    if (nfc == null) {
    	showDialog(DIALOG_NFC_NOT_AVAIL);
    }
    nfcintent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
}

@Override
protected void onResume() {
	super.onResume();
	nfc.enableForegroundDispatch(this, nfcintent, null, nfctechfilter);
}

@Override
protected void onPause() {
	super.onPause();
	nfc.disableForegroundDispatch(this);
}

protected void onNewIntent(Intent intent) {
	super.onNewIntent(intent);
	tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	
	log("Tag detected!");
	
	byte[] id = tag.getId();
	nfcid.setText(SharedUtils.Byte2Hex(id));
	
	tagcomm = IsoDep.get(tag);
	if (tagcomm == null) {
		toastError(getResources().getText(R.string.error_nfc_comm));
		return;
	}
	try {
		tagcomm.connect();
	} catch (IOException e) {
		toastError(getResources().getText(R.string.error_nfc_comm_cont) + (e.getMessage() != null ? e.getMessage() : "-"));
		return;
	}
	
	try {
		
		//Log.v("2PAY.SYS.DDF01", new String(transceive("00 a4 04 00 0e 32 50 41 59 2e 53 59 53 2e 44 44 46 30 31 00"), "ISO-8859-1"));
		// select 2PAY.SYS.DDF01	
		CardResponse response = transceive(EMVAPDUCommands.selectPPSE());
		rawdata.append("SELECT 2PAY.SYS.DDF01");
		rawdata.append(EMVUtil.getResponse(response, true)+"\n");

		byte recv[] = response.getData();
		for(int i = 0; i < recv.length; i++)
		{
			if(recv[i] == (byte) 0xBF && recv[i+1] == (byte) 0x0C)
			{
				int dlugosc = (int) recv[i+6];
				//AID = new String(SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, i+7, i+7+dlugosc)));
				//Log.v("AID", AID);
				AID = Arrays.copyOfRange(recv, i+7, i+7+dlugosc);
				break;
			}
		}

		if(AID.length > 0)
		{
			displayaid.setText(SharedUtils.Byte2Hex(AID));
			response =transceive(Iso7816Commands.selectByDFName(AID, false, AID[0]));
			rawdata.append("Selection by DF name");
			rawdata.append(EMVUtil.getResponse(response, true)+"\n");
			
			EMVApplication app = new EMVApplication();
			recv = response.getData(); 
			EMVUtil.parseFCIADF(recv, app);
			
			for(int i = 0 ; i< recv.length; i++)
			{
				if(recv[i] == (byte) 0x50)
				{
					int dlugosc = (int) recv[i+1];
					cardtype.setText(new String(Arrays.copyOfRange(recv, i+2, i+2+dlugosc)));
				}
			}

			readKreditkarte(app.getPDOL());
			zapiszDane();
		}
		else toastError(getResources().getText(R.string.error_card_unknown));

		// Switch to DF_BOERSE
/*		byte[] recv = transceive("00 A4 04 0C 09 D2 76 00 00 25 45 50 02 00");
		if (recv.length >= 2 && recv[0] == (byte) 0x90 && recv[1] == 0) {
			cardtype.setText("GeldKarte");
			readGeldKarte();
			return;
		} else 	if (new String(transceive("00 A4 04 00 07 A0 00 00 00 04 10 10"), "ISO-8859-1").contains("MasterCard")) {	// MasterCard
			cardtype.setText("MasterCard");
			readKreditkarte();
			
			// Now following: AIDs I never tried until now - perhaps they work, possibly not
		} else if (new String(transceive("00 A4 04 0C 07 A0 00 00 00 03 10 10"), "ISO-8859-1").length() > 2) {
			cardtype.setText("Visa");
			readKreditkarte();
		} else if (new String(transceive("00 A4 04 0C 07 A0 00 00 00 04 99 99"), "ISO-8859-1").length() > 2) {
			cardtype.setText("MasterCard");
			readKreditkarte();
		} else if (new String(transceive("00 A4 04 0C 07 A0 00 00 00 04 30 60"), "ISO-8859-1").length() > 2) {;
			cardtype.setText("Maestro");
			readKreditkarte();
		} else if (new String(transceive("00 A4 04 0C 07 A0 00 00 00 04 60 00"), "ISO-8859-1").length() > 2) {
			cardtype.setText("Cirrus");
			readKreditkarte();
		} else if (new String(transceive("00 A4 04 0C 07 A0 00 00 00 03 20 10"), "ISO-8859-1").length() > 2) {
			cardtype.setText("Visa Electron");
			readKreditkarte();
		} else if (new String(transceive("00 A4 04 0C 07 A0 00 00 00 03 20 20"), "ISO-8859-1").length() > 2) {
			cardtype.setText("Visa V Pay");
			readKreditkarte();
		} else if (new String(transceive("00 A4 04 0C 07 A0 00 00 00 03 80 10"), "ISO-8859-1").length() > 2) {
			cardtype.setText("Visa V Pay");
			readKreditkarte();
		} else {
			toastError(getResources().getText(R.string.error_card_unknown));
		}*/
		
		tagcomm.close();
	} catch (IOException e) {
		toastError(getResources().getText(R.string.error_nfc_comm_cont) + (e.getMessage() != null ? e.getMessage() : "-"));
	}
}
/*
private void readGeldKarte() {
	try  {
		// Read EF_BETRAG
		byte[] recv = transceive("00 B2 01 C4 00");
		betrag.setText(SharedUtils.formatBCDAmount(recv));

		// Read EF_ID
		recv = transceive("00 B2 01 BC 00");
		// Kartennr.
		kartennr.setText(SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 4, 9)).replace(" ", ""));
		//Aktiviert am
		aktiviert.setText(SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 14, 15)).replace(" ", "") + "." + SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 13, 14)).replace(" ", "") + ".20" + SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 12, 13)).replace(" ", ""));
		//Verfällt am
		verfall.setText(SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 11, 12)).replace(" ", "") + "/" + SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 10, 11)).replace(" ", ""));

		// EF_BÖRSE
		recv = transceive("00 B2 01 CC 00");
		// BLZ
		blz.setText(SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 1, 5)).replace(" ", ""));
		// Kontonr.
		konto.setText(SharedUtils.Byte2Hex(Arrays.copyOfRange(recv, 5, 10)).replace(" ", ""));

		transactions.setVisibility(View.VISIBLE);

		//		recv = transceive("00 A4 04 00 0E 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00");
		//		int len = recv.length;
		//		if (len >= 2 && recv[len - 2] == 0x90 && recv[len - 1] == 0) {
		//			// PSE supported
		//			addAIDRow(getResources().getText(R.string.ui_pse), getResources().getText(R.string.text_yes));
		//		} else {
		//			// no PSE
		//			addAIDRow(getResources().getText(R.string.ui_pse), getResources().getText(R.string.text_no));
		//		}
		//		recv = transceive("00 A4 04 0C 07 F0 00 00 01 57 10 21");	// Lastschrift AID
		//		recv = transceive("00 A4 04 0C 0A A0 00 00 03 59 10 10 02 80 01");	// EC AID
	} catch (IOException e) {
		toastError(getResources().getText(R.string.error_nfc_comm_cont) + (e.getMessage() != null ? e.getMessage() : "-"));			
	}
}
*/
private void readKreditkarte(DOL pdol) {
	try {
		//Get Processing Options: C-APDU
		CardResponse response = transceive(EMVAPDUCommands.getProcessingOpts(pdol));
		rawdata.append("Get Processing Options: C-APDU");
		rawdata.append(EMVUtil.getResponse(response, true)+"\n");
		EMVApplication app = new EMVApplication();
		EMVUtil.parseProcessingOpts(response.getData(), app);

		Log.v("ilosc rekordow", ""+app.getApplicationFileLocator().getApplicationElementaryFiles().size());
		if(app.getApplicationFileLocator().getApplicationElementaryFiles().size() > 100)
		{
			for(ApplicationElementaryFile AEF : app.getApplicationFileLocator().getApplicationElementaryFiles())
			{
				int start = AEF.getStartRecordNumber();
				int koniec = AEF.getEndRecordNumber();
				for(int i = start; i <= koniec; i++)
				{
					response = transceive(Iso7816Commands.readRecord(i, AEF.getSFI().getValue()));
					
					if(response.getData().length > 2)
					{
						if(kknr.getText().toString().length() == 0 || verfall.getText().toString().length() == 0 || owner.getText().toString().length() == 0)
						{
							app = new EMVApplication();
							EMVUtil.parseAppRecord(response.getData(), app);
							
							if(kknr.getText().toString().length() == 0)
							{
								PAN pan = app.getPAN();
								if(pan != null && pan.isValid()) kknr.setText(pan.getPanAsString());
							}
							if(verfall.getText().toString().length() == 0)
							{
								Date date = app.getExpirationDate();
								if(date != null) verfall.setText(date.toString());
							}
							if(owner.getText().toString().length() == 0)
							{
								String wlasciciel = app.getCardholderName();
								if(wlasciciel != null && wlasciciel.length() > 5) owner.setText(wlasciciel);
							}
						}
		
						//Log.v("SFI "+AEF.getSFI().getValue()+" "+start+"/"+koniec, new String(response.getData()));
						rawdata.append("SFI "+AEF.getSFI().getValue()+" "+i+"/"+koniec+"\n");
						rawdata.append(EMVUtil.getResponse(response, true)+"\n");
						
						if(AEF.getSFI().getValue() == 1 && i == 1)
						{
							response = transceive(EMVAPDUCommands.getPINTryConter());
							rawdata.append(EMVUtil.getResponse(response, true)+"\n");
							response = transceive(EMVAPDUCommands.getApplicationTransactionCounter());
							rawdata.append(EMVUtil.getResponse(response, true)+"\n");
							// rozpoczecie transakcji
							rawdata.append("Compute Cryptographic Checksum: C-APDU");
							response = transceive("80 2a 8e 80 04 00 00 00 80 00");
							rawdata.append(EMVUtil.getResponse(response, true)+"\n");
						}
					}
				}
			}
		}
		// alternatywna metoda odczytu wszystkich rekordow
		else
		{
			for(int sfi = 1; sfi <30; sfi++)
			{
				int start = 1;
				int koniec = 255;
				for(int i = start; i <= koniec; i++)
				{
					response = transceive(Iso7816Commands.readRecord(i, sfi));
					
					if(response.getData().length > 2)
					{
						if(kknr.getText().toString().length() == 0 || verfall.getText().toString().length() == 0 || owner.getText().toString().length() == 0)
						{
							app = new EMVApplication();
							EMVUtil.parseAppRecord(response.getData(), app);
							
							if(kknr.getText().toString().length() == 0)
							{
								PAN pan = app.getPAN();
								if(pan != null && pan.isValid()) kknr.setText(pan.getPanAsString());
							}
							if(verfall.getText().toString().length() == 0)
							{
								Date date = app.getExpirationDate();
								if(date != null) verfall.setText(date.toString());
							}
							if(owner.getText().toString().length() == 0)
							{
								String wlasciciel = app.getCardholderName();
								if(wlasciciel != null && wlasciciel.length() > 5) owner.setText(wlasciciel);
							}
						}
						//Log.v("SFI "+AEF.getSFI().getValue()+" "+start+"/"+koniec, new String(response.getData()));
						rawdata.append("SFI "+sfi+" "+i+"\n");
						rawdata.append(EMVUtil.getResponse(response, true)+"\n");
						
						if(sfi == 1 && i == 1)
						{
							//response = transceive(EMVAPDUCommands.verifyPIN(123456, true));
							//rawdata.append(EMVUtil.getResponse(response, true)+"\n");
							response = transceive(EMVAPDUCommands.getPINTryConter());
							rawdata.append(EMVUtil.getResponse(response, true)+"\n");
							response = transceive(EMVAPDUCommands.getApplicationTransactionCounter());
							rawdata.append(EMVUtil.getResponse(response, true)+"\n");
							// rozpoczecie transakcji
							rawdata.append("Compute Cryptographic Checksum: C-APDU");
							response = transceive("80 2a 8e 80 04 00 00 00 80 00");
							rawdata.append(EMVUtil.getResponse(response, true)+"\n");
						}
						
					}else break;
				}
			}
		}


			} catch (IOException e) {
		toastError(getResources().getText(R.string.error_nfc_comm_cont) + (e.getMessage() != null ? e.getMessage() : "-"));
	}
}


public static CardResponse transceive(String hexstr) throws IOException {
	String[] hexbytes = hexstr.split("\\s");
	byte[] bytes = new byte[hexbytes.length];
	for (int i = 0; i < hexbytes.length; i++) {
		bytes[i] = (byte) Integer.parseInt(hexbytes[i], 16);
	}
	log("Send: " + SharedUtils.Byte2Hex(bytes));
	byte[] recv = tagcomm.transceive(bytes);
	log("Received: " + SharedUtils.Byte2Hex(recv));
	
    CardResponse response = new SECardResponse(recv);
    EMVUtil.printResponse(response, true);    	

    return response;
}

public static CardResponse transceive(byte[] bytes) throws IOException {
	log("Send: " + SharedUtils.Byte2Hex(bytes));
	byte[] recv = tagcomm.transceive(bytes);
	log("Received: " + SharedUtils.Byte2Hex(recv));
	//return recv;
	
    CardResponse response = new SECardResponse(recv);
    EMVUtil.printResponse(response, true);

    return response;
}

protected Dialog onCreateDialog(int id) {
	Dialog dialog;
	
	switch (id) {
	case DIALOG_NFC_NOT_AVAIL:
		dialog = new AlertDialog.Builder(this)
		.setMessage(getResources().getText(R.string.error_nfc_not_avail))
		.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				MainActivity.this.finish();
			}
		})
		.create();
		break;
	default:
		dialog = null;
	break;		
	}
	
	return dialog;
}

private View.OnClickListener transactions_click = new View.OnClickListener() {

	@Override
	public void onClick(View v) {
/*		Intent intent = new Intent(MainActivity.this, TransactionsActivity.class);
		try  {
			// Read all EF_BLOG records
			for (int i = 1; i <= 15; i++) {
				byte[] recv = transceive(String.format("00 B2 %02x EC 00", i));
				intent.putExtra(String.format("blog_%d", i), recv);
			}
			startActivity(intent);
		} catch (IOException e) {
			toastError(getResources().getText(R.string.error_nfc_comm_cont) + (e.getMessage() != null ? e.getMessage() : "-"));
		}*/
	}
	
};

private void addAIDRow(CharSequence left, CharSequence right) {
	TextView t1 = new TextView(this);
	t1.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	t1.setPadding(0, 0, (int) (getResources().getDisplayMetrics().density * 10 + 0.5f), 0);
	t1.setTextAppearance(this, android.R.attr.textAppearanceMedium);
	t1.setText(left);
	
	TextView t2 = new TextView(this);
	t2.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	t2.setText(right);
	
	TableRow tr = new TableRow(this);
	tr.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	tr.addView(t1);
	tr.addView(t2);

}

public int zapiszDane()
{
	String state = Environment.getExternalStorageState();

	if (Environment.MEDIA_MOUNTED.equals(state)) {
	    // We can read and write the media
		try
		{
		    String nazwa_pliku = Environment.getExternalStorageDirectory().toString() + "/paypass/" +pobierzNazwePliku();
		    File dir = new File (Environment.getExternalStorageDirectory().toString() + "/paypass");
		    if(dir.exists() == false) dir.mkdir();
		    FileOutputStream fos = new FileOutputStream(nazwa_pliku);
		    int BUFFER_SIZE = 8192;
		    byte[] bom = new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF }; // BOM values 
		    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(fos,Charset.forName("UTF-8")),BUFFER_SIZE); 
		    
		    fos.write(bom);
	        out.write(getString(R.string.ui_nfcid) + ": " + nfcid.getText());
	        out.newLine();
	        out.write(getString(R.string.ui_aid) + ": " + displayaid.getText());
	        out.newLine();
	        out.write(getString(R.string.ui_cardtype) + ": " + cardtype.getText());
	        out.newLine();
	        out.write(getString(R.string.ui_verfall) + ": " + verfall.getText());
	        out.newLine();
	        out.write(getString(R.string.ui_kk) + ": " + kknr.getText());
	        out.newLine();
	        out.write(getString(R.string.ui_owner) + ": " + owner.getText());
	        out.newLine();
	        out.newLine();
	        out.write("Dane z karty: ");
	        out.newLine();
	        out.write(rawdata.getText().toString());
	        out.close();

	        return 0;
		} catch (IOException e) {
			return 2;
		}			
	} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
		return 1;
	} else {
	    // Something else is wrong. It may be one of many other states, but all we need
	    //  to know is we can neither read nor write
		return 2;
		
	}
}

public String pobierzNazwePliku()
{
	Date dataWyslania = new Date();
	SimpleDateFormat format = new SimpleDateFormat("ddMMyy_HHmmss");
	String numer = nfcid.getText().toString().replace(" ", "");
	if(numer.length() > 0) return numer + "_" + format.format(dataWyslania) + ".txt";
	else return format.format(dataWyslania) + ".txt";
}

public static void log(String msg) {
	Log.d(LOGTAG, msg);
}

protected void toastError(CharSequence msg) {
	Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
}
}