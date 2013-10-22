package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.typedobj.core.validatorconfig.WsIdRefValidationBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jsonschema.report.LogLevel;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;


/**
 * The report generated when a typed object instance is validated.  If the type definition indicates
 * that fields are ID references, those ID references can be extracted from this report.  If a
 * searchable subset flag is set in the type definition, you can extract that too.
 *
 * @author msneddon
 */
public class TypedObjectValidationReport {

	/**
	 * tunable parameter for initializing the list of IDs; we may get minor performance gains by tweaking this
	 */
	private final static int EXPECTED_NUMBER_OF_IDS = 25;
	/**
	 * tunable parameter for initializing the list of IDs; we may get minor performance gains by tweaking this
	 */
	private final static int EXPECTED_OBJ_DEPTH = 5;
	
	/**
	 * the report object generated by the json-schema-validator library, which is the core object we are wrapping
	 */
	protected ProcessingReport processingReport;
	
	/**
	 * This is the ID of the type definition used in validation - it is an AbsoluteTypeDefId so you always have full version info
	 */
	private final AbsoluteTypeDefId validationTypeDefId;
	
	/**
	 * List of Lists of IDs that were extracted from the instance, built from the processingReport only if requested.
	 * The position in the first list indicates the depth of the ID position.  This is required because when IDs are
	 * renamed, we must rename them in a depth first order (if you care, this is because IDs can be keys in mappings,
	 * which are indistinguishable from JSON objects, so paths to deeper IDs will be incorrect if a key at a higher
	 * level in the path is renamed)
	 */
	private List< List<IdReference> > idReferences;
	private String[] simpleIdList;
	private boolean idRefListIsBuilt;
	
	
	/**
	 * Initialize with the given processingReport (created when a JsonSchema is used to validate) and the
	 * typeDefId of the typed object definition used when validating.
	 * @param processingReport
	 * @param validationTypeDefId
	 */
	public TypedObjectValidationReport(ProcessingReport processingReport, AbsoluteTypeDefId validationTypeDefId) {
		this.processingReport=processingReport;
		this.idRefListIsBuilt=false;
		this.validationTypeDefId=validationTypeDefId;
	}
	
	/**
	 * Get the absolute ID of the typedef that was used to validate the instance
	 * @return
	 */
	public AbsoluteTypeDefId getValidationTypeDefId() {
		return validationTypeDefId;
	}
	
	/**
	 * @return boolean true if the instance is valid, false otherwise
	 */
	public boolean isInstanceValid() {
		return processingReport.isSuccess();
	}
	
	/**
	 * Iterate over all items in the report and count the errors.
	 * @return n_errors
	 */
	public int getErrorCount() {
		if(isInstanceValid()) { return 0; }
		Iterator<ProcessingMessage> mssgs = processingReport.iterator();
		int n_errors=0;
		while(mssgs.hasNext()) {
			ProcessingMessage pm = mssgs.next();
			if(pm.getLogLevel().equals(LogLevel.ERROR)) {
				n_errors++;
			}
		}
		return n_errors;
	}
	
	/**
	 * Iterate over all items in the report and return the error messages.
	 * @return n_errors
	 */
	public String [] getErrorMessages() {
		if(isInstanceValid()) { return new String[0]; }
		
		Iterator<ProcessingMessage> mssgs = processingReport.iterator();
		ArrayList <String> errMssgs = new ArrayList<String>();
		
		while(mssgs.hasNext()) {
			ProcessingMessage pm = mssgs.next();
			if(pm.getLogLevel().equals(LogLevel.ERROR)) {
				errMssgs.add(pm.getMessage());
			}
		}
		return errMssgs.toArray(new String[errMssgs.size()]);
	}
	
	
	
	/**
	 * This method returns the raw report generated by the JsonSchema, useful in some cases if
	 * you need to dig down into the guts of keywords or to investigate why something failed.
	 */
	public ProcessingReport getRawProcessingReport() {
		return processingReport;
	}
	
	
	
	/**
	 * <p>Returns the full information about each ID Reference, most useful of which is the location in the instance
	 * where the ID Reference was identified.  The data is returned as a list of lists where the position in the 
	 * first list indicates the depth of the ID position.  This is required because when IDs are renamed, we must 
	 * rename them in a depth first order (if you care, this is because IDs can be keys in KIDL mappings,
	 * which are indistinguishable from JSON objects, so paths to deeper IDs will be incorrect if a key at a higher
	 * level in the path is renamed first).</p>
	 * 
	 * <p>If you just want a simple list of the IDs, use {@link #getListOfIdReferences() getListOfIdReferences}  instead!</p>
	 */
	public List<List<IdReference>> getListOfIdReferenceObjects() {
		if(!idRefListIsBuilt) {
			buildIdList();
		}
		return this.idReferences;
	}
	
	/**
	 * Return a simple list of all fields that were flagged as ID References.
	 * @return
	 */	
	public List<String> getListOfIdReferences() {
		if(!idRefListIsBuilt) {
			buildIdList();
		}
		//TODO change simpleIdList to a List
		final List<String> ids = new LinkedList<String>();
		for (int i = 0; i < simpleIdList.length; i++) {
			ids.add(simpleIdList[i]);
		}
		return ids;
	}
	
	
	/**
	 * Set the absolute ID References as specified in the absoluteIdRefMapping (original ids
	 * are keys, replacement absolute IDs are values).
	 */
	public void setAbsoluteIdReferences(Map<String,String> absoluteIdRefMapping) {
		if(!idRefListIsBuilt) {
			buildIdList();
		}
		// traverse all the ID objects, and set the absolute ID mapping if available
		for(List<IdReference> refList : idReferences) {
			for(IdReference ref : refList) {
				String absIdRef = absoluteIdRefMapping.get(ref.getIdReference());
				if(absIdRef!=null) {
					ref.setAbsoluteId(absIdRef);
				}
			}
		}
	}
	
	
	/**
	 * given the internal processing report, compute the list of IDs
	 */
	protected void buildIdList() {
		// initialize the lists
		idReferences = new ArrayList<List<IdReference>>(EXPECTED_OBJ_DEPTH);
		List<String> simpleIdListBuilder = new ArrayList<String>(EXPECTED_NUMBER_OF_IDS);
		
		// process each message that lists an ID
		Iterator<ProcessingMessage> mssgs = processingReport.iterator();
		while(mssgs.hasNext()) {
			ProcessingMessage m = mssgs.next();
			if( m.getMessage().compareTo(WsIdRefValidationBuilder.keyword) != 0 ) {
				continue;
			}
			
			//construct the IdReference object
			String id = m.asJson().get("id").asText();
			simpleIdListBuilder.add(id);
			JsonNode typeNames = m.asJson().get("type");
			ArrayList<TypeDefId> typesList = new ArrayList<TypeDefId>(typeNames.size());
			for(int k=0; k<typeNames.size(); k++) {
				String fullName = typeNames.get(k).asText();
				typesList.add(new TypeDefId(new TypeDefName(fullName)));
			}
			ArrayNode path = (ArrayNode)m.asJson().get("location");
			IdReference idRef = new IdReference((ArrayNode)path,id,typesList,m.asJson().get("is-mapping-key").asBoolean());
			
			// make sure our storage container can go deep enough
			int depth = path.size()-1;
			while(idReferences.size()<depth+1) {
				idReferences.add(new ArrayList<IdReference>(EXPECTED_NUMBER_OF_IDS));
			}
			
			// push the idRef to the list at the proper depth
			idReferences.get(depth).add(idRef);
		}
		
		// convert the simple list to an array
		this.simpleIdList = simpleIdListBuilder.toArray(new String[simpleIdListBuilder.size()]);
		
		//done and done.
		idRefListIsBuilt=true;
	}
	
	
	
	@Override
	public String toString() {
		StringBuilder mssg = new StringBuilder();
		mssg.append("TYPED OBJECT VALIDATION REPORT\n");
		mssg.append(" -validated instance against: '"+validationTypeDefId.getTypeString()+"'\n");
		mssg.append(" -status: ");
		if(this.isInstanceValid()) {
			mssg.append("pass\n");
			mssg.append(" -id refs extracted: "+simpleIdList.length);
		}
		else {
			String [] errs = this.getErrorMessages();
			mssg.append("fail ("+errs.length+" error(s))\n");
			for(int k=0; k<errs.length; k++) {
				mssg.append(" -["+(k+1)+"]:"+errs[k]);
			}
		}
		return mssg.toString();
	}
	
	
}
