package strafford_SOM_PKG.straff_RawDataHandling.data_loaders;

import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_ProspectData;

//stream prospects and build the objects that will then decipher their json content and build the training/testing data based on them
public class Straff_ProspectDataLoader extends Straff_RawDataLoader{
	public Straff_ProspectDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected Straff_BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		Straff_ProspectData obj = new Straff_ProspectData(msgObj,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
