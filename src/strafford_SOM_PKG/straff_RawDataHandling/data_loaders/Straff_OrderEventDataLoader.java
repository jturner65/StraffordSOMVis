package strafford_SOM_PKG.straff_RawDataHandling.data_loaders;

import strafford_SOM_PKG.straff_RawDataHandling.data_loaders.base.Straff_RawDataLoader;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_OrderEvent;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;

//stream Order Events and build the objects that will then decipher their json content and build the training/testing data based on them
public class Straff_OrderEventDataLoader extends Straff_RawDataLoader{
	public Straff_OrderEventDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected Straff_BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		Straff_OrderEvent obj = new Straff_OrderEvent(msgObj,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
