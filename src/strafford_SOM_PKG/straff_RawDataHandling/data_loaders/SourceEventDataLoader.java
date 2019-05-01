package strafford_SOM_PKG.straff_RawDataHandling.data_loaders;

import strafford_SOM_PKG.straff_RawDataHandling.BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.SourceEvent;

//stream opt Events and build the objects that will then decipher their json content and build the training/testing data based on them
public class SourceEventDataLoader extends StraffordDataLoader{
	public SourceEventDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		SourceEvent obj = new SourceEvent(msgObj,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}

}//