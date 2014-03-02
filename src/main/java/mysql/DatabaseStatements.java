package mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseStatements {
	
	/*methods for the creation of various prepared statements*/
	
	public int createForm(Connection con, String name, String version, String description, int encounterType, int creator, UUID uuid) {
		PreparedStatement formStatement=null;
		ResultSet rs=null;
		int formId=0;
		try {			
			formStatement = con.prepareStatement("insert into  openmrs.form (name, version, " +
							"published, description, encounter_type, creator, date_created, retired, uuid) " +
							"values (?, ?, default, ? , ?, ?, ?, default, ?)", Statement.RETURN_GENERATED_KEYS);
			formStatement.setString(1, name);
			formStatement.setString(2, version);
			formStatement.setString(3, description);
			formStatement.setInt(4, encounterType);
			formStatement.setInt(5, creator);						
			long timeNow = Calendar.getInstance().getTimeInMillis();
			java.sql.Timestamp ts = new java.sql.Timestamp(timeNow);
			formStatement.setTimestamp(6, ts);
			formStatement.setString(7, uuid.toString());
			formStatement.executeUpdate();
			rs = formStatement.getGeneratedKeys(); 
			rs.next();	
			formId=rs.getInt(1);
		} catch (SQLException exc) {
        	System.out.println("SQLException: " + exc.getMessage());
        	System.out.println("SQLState: " + exc.getSQLState());
        	System.out.println("VendorError: " + exc.getErrorCode());	
        	exc.printStackTrace();
		} finally { //closing the resources in this transaction
	        try { 
	            if (formStatement != null) {
	            	formStatement.close();
	            }
	            if (rs != null) {
	            	rs.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        	sqle.printStackTrace();
	        }
		}
	    return formId;
	}
	
	public int createFormField(Connection con, int formId, int fieldId, Integer fieldNumber, Integer parentFormField, int creator) {
		ResultSet rs=null;
		PreparedStatement formFieldStatement=null;
		int formFieldId=0;
		try {
		formFieldStatement = con.prepareStatement("insert into  openmrs.form_field (form_id, field_id, " +
						"field_number, parent_form_field, required, creator, date_created, sort_weight, uuid) " +
						"values (?, ?, ?, ? , default, ?, ?, default, ?)", Statement.RETURN_GENERATED_KEYS);
		
		/*formFields with number 2 and 3 are created first, as they are parent fields of other formFields*/
		formFieldStatement.setInt(1, formId);
		formFieldStatement.setInt(2, fieldId); 
		if(fieldNumber!=null){
			formFieldStatement.setInt(3, fieldNumber);
		} else {
			formFieldStatement.setNull(3, java.sql.Types.INTEGER);
		}		
		if(parentFormField!=null) {
			formFieldStatement.setInt(4, parentFormField);
		} else {
			formFieldStatement.setNull(4, java.sql.Types.INTEGER);
		}
		formFieldStatement.setInt(5, creator);
		long timeNow = Calendar.getInstance().getTimeInMillis();
		java.sql.Timestamp ts = new java.sql.Timestamp(timeNow);
		formFieldStatement.setTimestamp(6, ts);
		formFieldStatement.setString(7, UUID.randomUUID().toString());
		formFieldStatement.executeUpdate();
		rs = formFieldStatement.getGeneratedKeys();
		rs.next();
		formFieldId=rs.getInt(1);
		} catch (SQLException exc) {
        	System.out.println("SQLException: " + exc.getMessage());
        	System.out.println("SQLState: " + exc.getSQLState());
        	System.out.println("VendorError: " + exc.getErrorCode());	
		} finally {
			try {
	            if (formFieldStatement != null) {
	            	formFieldStatement.close();
	            }
	            if (rs != null) {
	            	rs.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}
		return formFieldId;
	}
	
	/*Entry in xforms_xform table is created*/
	public void createXForm(Connection con, int formId, String xFormXml, String layoutXml, int creator, UUID uuid) {
		
		PreparedStatement xformStatement=null;
		try {						
			xformStatement = con.prepareStatement("insert into  openmrs.xforms_xform (form_id, xform_xml, layout_xml, " +
							"creator, date_created, uuid) values (?, ?, ?, ? , ?, ?)");
			xformStatement.setInt(1, formId);
			xformStatement.setString(2, xFormXml);
			xformStatement.setString(3, layoutXml);
			xformStatement.setInt(4, creator);						
			long timeNow = Calendar.getInstance().getTimeInMillis();
			java.sql.Timestamp ts = new java.sql.Timestamp(timeNow);
			xformStatement.setTimestamp(5, ts);
			xformStatement.setString(6, uuid.toString());
			xformStatement.executeUpdate();
			
		} catch (SQLException exc) {
        	System.out.println("SQLException: " + exc.getMessage());
        	System.out.println("SQLState: " + exc.getSQLState());
        	System.out.println("VendorError: " + exc.getErrorCode());	
		} finally { //closing the resources in this transaction
	        try {
	            if (xformStatement != null) {
	            	xformStatement.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}		
	}
		
	public int createConcept(Connection con, String description, int dataTypeId, int classId, int creator, boolean isSet) {
		PreparedStatement conceptStatement=null;
		ResultSet rs=null;
		int conceptId=0;
		try { 
			conceptStatement = con.prepareStatement("insert into  openmrs.concept (description, " +
							"datatype_id, class_id, creator, date_created, uuid, is_set) " +
							"values (?, ? , ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			conceptStatement.setString(1, description);
			conceptStatement.setInt(2, dataTypeId);	
			conceptStatement.setInt(3, classId);
			conceptStatement.setInt(4, creator);	
			long timeNow = Calendar.getInstance().getTimeInMillis();
			java.sql.Timestamp ts = new java.sql.Timestamp(timeNow);
			conceptStatement.setTimestamp(5, ts);
			conceptStatement.setString(6, UUID.randomUUID().toString());			
			if(isSet){
				conceptStatement.setInt(7, 1);
			} else {
				conceptStatement.setInt(7, 0);
			}
			conceptStatement.executeUpdate();
			rs = conceptStatement.getGeneratedKeys();
			rs.next();
			conceptId=rs.getInt(1);
		} catch(SQLException exc) {
			System.out.println("SQLException: " + exc.getMessage());
			System.out.println("SQLState: " + exc.getSQLState());
			System.out.println("VendorError: " + exc.getErrorCode());		
		} finally {
	        try {
	            if (conceptStatement != null) {
	            	conceptStatement.close();
	            }
	            if (rs != null) {
	            	rs.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}
		return conceptId;
	}
	
	public void createConceptName(Connection con, int conceptId, String name, int creator) {
		PreparedStatement conceptNameStatement=null;
		try { 
			conceptNameStatement = con.prepareStatement("insert into  openmrs.concept_name (concept_id, name, " +
							"creator, date_created, uuid, concept_name_type) " +
							"values (?, ? , ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			
			conceptNameStatement.setInt(1, conceptId);
			conceptNameStatement.setString(2, name);
			conceptNameStatement.setInt(3, creator);
			long timeNow = Calendar.getInstance().getTimeInMillis();
			java.sql.Timestamp ts = new java.sql.Timestamp(timeNow);
			conceptNameStatement.setTimestamp(4, ts);
			conceptNameStatement.setString(5, UUID.randomUUID().toString());
			conceptNameStatement.setString(6, "FULLY_SPECIFIED");
			conceptNameStatement.executeUpdate();	
		} catch(SQLException exc) {
			System.out.println("SQLException: " + exc.getMessage());
			System.out.println("SQLState: " + exc.getSQLState());
			System.out.println("VendorError: " + exc.getErrorCode());		
		} finally {
	        try {
	            if (conceptNameStatement != null) {
	            	conceptNameStatement.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}
	}
	
	public void createConceptSet(Connection con, int conceptId, int setConceptId, int creator) {
		PreparedStatement conceptSetStatement=null;
		try {			
			conceptSetStatement = con.prepareStatement("insert into  openmrs.concept_set (concept_id, " +
								"concept_set, creator, date_created, uuid) " +
								"values (?, ? , ?, ?, ?)");	
			conceptSetStatement.setInt(1, conceptId);			
			conceptSetStatement.setInt(2, setConceptId);
			conceptSetStatement.setInt(3, creator);
			long timeNow = Calendar.getInstance().getTimeInMillis();
			java.sql.Timestamp ts = new java.sql.Timestamp(timeNow);
			conceptSetStatement.setTimestamp(4, ts);
			conceptSetStatement.setString(5, UUID.randomUUID().toString());
			conceptSetStatement.executeUpdate();				
		} catch (SQLException exc) {
	        System.out.println("SQLException: " + exc.getMessage());
	        System.out.println("SQLState: " + exc.getSQLState());
	        System.out.println("VendorError: " + exc.getErrorCode());		
		} finally { 
	        try {
	            if (conceptSetStatement != null) {
	            	conceptSetStatement.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}
	}
	
	public void createConceptAnswer(Connection con, int conceptId, int answerConceptId, int creator) {
		PreparedStatement conceptAnswerStatement=null;
		try {			
			conceptAnswerStatement = con.prepareStatement("insert into  openmrs.concept_answer (concept_id, " +
								"answer_concept, creator, date_created, uuid) " +
								"values (?, ? , ?, ?, ?)");	
			conceptAnswerStatement.setInt(1, conceptId);			
			conceptAnswerStatement.setInt(2, answerConceptId);
			conceptAnswerStatement.setInt(3, creator);
			long timeNow = Calendar.getInstance().getTimeInMillis();
			java.sql.Timestamp ts = new java.sql.Timestamp(timeNow);
			conceptAnswerStatement.setTimestamp(4, ts);
			conceptAnswerStatement.setString(5, UUID.randomUUID().toString());
			conceptAnswerStatement.executeUpdate();				
		} catch (SQLException exc) {
	        System.out.println("SQLException: " + exc.getMessage());
	        System.out.println("SQLState: " + exc.getSQLState());
	        System.out.println("VendorError: " + exc.getErrorCode());		
		} finally { 
	        try {
	            if (conceptAnswerStatement != null) {
	            	conceptAnswerStatement.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}		
	}
	
	public int createOpenEHRFormMetadata(Connection con, int formId, String widgetType, String label, String xPath, int conceptId, String defaultValue, int inRepeat) {
		PreparedStatement conceptSetStatement=null;
		ResultSet rs=null;
		int metadataId=0;
		try {			
			conceptSetStatement = con.prepareStatement("insert into  openmrs.openehr_form_metadata (form_id, " +
								"widget_type, label, path, uuid, concept_id, default_value, in_repeat) " +
								"values (?, ? , ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);	
			conceptSetStatement.setInt(1, formId);			
			conceptSetStatement.setString(2, widgetType);
			conceptSetStatement.setString(3, label);
			conceptSetStatement.setString(4, xPath);
			conceptSetStatement.setString(5, UUID.randomUUID().toString());
			if(conceptId!=0) { //concept_id=0, if none is available
				conceptSetStatement.setInt(6, conceptId);	
			} else {
				conceptSetStatement.setNull(6, java.sql.Types.INTEGER);
			}
			if(defaultValue!=null) {
				conceptSetStatement.setString(7, defaultValue);	
			} else {
				conceptSetStatement.setNull(7, java.sql.Types.VARCHAR);
			}			
			//if(inRepeat) {
				conceptSetStatement.setInt(8, inRepeat);
			//} else {
				//conceptSetStatement.setInt(8, 0);
			//}
			conceptSetStatement.executeUpdate();
			rs = conceptSetStatement.getGeneratedKeys();
			rs.next();
			metadataId=rs.getInt(1);
		} catch (SQLException exc) {
	        System.out.println("SQLException: " + exc.getMessage());
	        System.out.println("SQLState: " + exc.getSQLState());
	        System.out.println("VendorError: " + exc.getErrorCode());		
		} finally { 
	        try {
	            if (conceptSetStatement != null) {
	            	conceptSetStatement.close();
	            }
	            if (rs != null) {
	            	rs.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}
		return metadataId;
	}

	/*datatype of the concept is changed (e.g. if it is only know after concept creation that a ListBox is needed*/
	public void updateConceptDatatype(Connection con, int datatypeId, int conceptId) {
		PreparedStatement conceptDatatypeStatement=null;
		try { 
			conceptDatatypeStatement = con.prepareStatement("update  openmrs.concept set datatype_id=? where concept_id=?");	
			conceptDatatypeStatement.setInt(1, datatypeId);
			conceptDatatypeStatement.setInt(2, conceptId);
			conceptDatatypeStatement.executeUpdate();
		} catch(SQLException exc) {
			System.out.println("SQLException: " + exc.getMessage());
			System.out.println("SQLState: " + exc.getSQLState());
			System.out.println("VendorError: " + exc.getErrorCode());		
		} finally {
	        try {
	            if (conceptDatatypeStatement != null) {
	            	conceptDatatypeStatement.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}
	}
	
	/*retrieves the data entered in the form and saves it into a HashMap together with the XPaths*/
	public LinkedHashMap<String,String> getObsAndPathsFromEncounterId(Connection con, int encounterId) {		
		PreparedStatement encounterStatement=null;
		PreparedStatement dataStatement=null;
		PreparedStatement conceptNameStatement=null;
		ResultSet rs=null;
		ResultSet rs1=null;
		int formId=0;
		LinkedHashMap<String,String> pathsAndObs = new LinkedHashMap<String,String>();
		try { 
			encounterStatement = con.prepareStatement("select form_id from openmrs.encounter where encounter_id=?");
			encounterStatement.setInt(1, encounterId);
			rs=encounterStatement.executeQuery();
			while(rs.next()) {
				formId=rs.getInt("form_id");
			}

			String selectObsAndPathsSQL=
					"SELECT a.obs_id, a.obs_group_id, a.value_boolean, a.value_coded, "
					+"a.value_coded_name_id, a.value_datetime, a.value_numeric, a.value_text, "
					+"b.metadata_id, b.concept_id, b.widget_type, b.path, b.default_value, b.in_repeat "
					+"FROM (SELECT * FROM openmrs.obs WHERE encounter_id=?) a " 
					+"RIGHT JOIN "
					+"(SELECT * FROM openmrs.openehr_form_metadata WHERE form_id=?) AS b "
					+"ON a.concept_id=b.concept_id ";
			dataStatement = con.prepareStatement(selectObsAndPathsSQL);
			dataStatement.setInt(1, encounterId);
			dataStatement.setInt(2, formId);		
			rs1=dataStatement.executeQuery();
			
			HashMap<Integer, String> repeatPaths = new HashMap<Integer, String>(); /*includes obsId and Path of repeat elements*/
			HashMap<String, Integer> repeatPathsNo = new HashMap<String, Integer>(); /*includes the path of repeat elements and how often the element has appeared yet (important for path adaption)*/
			HashMap<Integer, String> repeatPathsMetadata = new HashMap<Integer, String>(); /*includes metadataId and path of repeat elements*/
			
			/*the ResultSet is run through once to find out the paths of the repeat elements*/
			rs1.first();
			do {
				/*if its a repeat element, the path needs to be adapted*/
				if(rs1.getString("widget_type")!=null && rs1.getString("widget_type").equalsIgnoreCase("Repeat")) {
					String path=rs1.getString("path");
					repeatPathsMetadata.put(rs1.getInt("metadata_id"), path);
					if(repeatPathsNo.containsKey(path)) { /*an entry for this repeat element is already existing*/
						int repeatNo=repeatPathsNo.get(path)+1;
						repeatPaths.put(rs1.getInt("obs_id"), path+"["+repeatNo+"]");
						repeatPathsNo.put(path, repeatNo);
					} else {
						repeatPaths.put(rs1.getInt("obs_id"), path+"[1]");
						repeatPathsNo.put(path, 1);
					}	
				}
			} while(rs1.next());
			
			rs1.first();
			do {
				int inRepeat=rs1.getInt("in_repeat"); /*includes the metadata id of the surrounding repeat element in which the element*/
				String rsPath=rs1.getString("path");
				String finalObsValue=null;
				int rsObsId=rs1.getInt("obs_id");
				if(rs1.wasNull() && inRepeat>0) { /*if the element is in a repeat element, the path needs to be adapted*/				
					if(repeatPathsMetadata.get(inRepeat)!=null) {
						String repeatPath=repeatPathsMetadata.get(inRepeat);
						int numberRepeats=repeatPathsNo.get(repeatPath);
						for(int i=1; i<=numberRepeats; i++) { /*the entry is multiplied, depending on the number of entered values*/
							String newPath=rsPath.replace(repeatPath, repeatPath+"["+i+"]");
							String value=rs1.getString("default_value");
							if(!rs1.wasNull()) {
								pathsAndObs.put(newPath, value);
							} else {
								pathsAndObs.put(newPath, "");
							}
						}
					} else { /*the according repeat paths couldn't be found, another way of searching is used*/
						for(Map.Entry<String, Integer> entry: repeatPathsNo.entrySet()) {
							if(rsPath.contains(entry.getKey())) {/*if one of the repeat paths is included, the entry is multiplied, depending on the number of repeats*/						
								for(int i=1; i<=entry.getValue(); i++) {
									String newPath=rsPath.replace(entry.getKey(), entry.getKey()+"["+i+"]");
									String value=rs1.getString("default_value");
									if(!rs1.wasNull()) {
										pathsAndObs.put(newPath, value);
									} else {
										pathsAndObs.put(newPath, "");
									}
								}
							}
						}					
					}
				} else {
								
					String widgetType=rs1.getString("widget_type");
					if(!rs1.wasNull() && widgetType.equalsIgnoreCase("Repeat")) { /*looking for the correct path*/
						String newPath=repeatPaths.get(rsObsId);
						String oldPath=newPath.substring(0,newPath.lastIndexOf('['));
						rsPath=rsPath.replace(oldPath, newPath);		
					}
									
					int rsObsGroupId=rs1.getInt("obs_group_id"); 
					if(!rs1.wasNull()) { /*form field is in a repeat section and the path needs to be adapted*/
						String newPath=repeatPaths.get(rsObsGroupId);
						String oldPath=newPath.substring(0,newPath.lastIndexOf('['));
						/*looking for the old repeat path in the path of the element and replacing it with the new repeat path*/
						rsPath=rsPath.replace(oldPath, newPath);	
					}

					/*checking whether the value column includes a null or a value
					 * adding path and value in the HashMap*/
					rsObsId=rs1.getInt("obs_id");
					if(!rs1.wasNull()) { /*Obs for this path are available*/
						boolean rsValueBoolean=rs1.getBoolean("value_boolean");
						if(!rs1.wasNull()) { 
							finalObsValue=String.valueOf(rsValueBoolean);
						}
						conceptNameStatement=con.prepareStatement("select name from openmrs.concept_name where concept_name_id=?");
						int rsValueCodedNameId=rs1.getInt("value_coded_name_id");
						if(!rs1.wasNull()) { 
							conceptNameStatement=con.prepareStatement("select name from openmrs.concept_name where concept_name_id=?");
							conceptNameStatement.setInt(1, rsValueCodedNameId);
							rs=conceptNameStatement.executeQuery();
							while(rs.next()) { 
								finalObsValue=rs.getString("name"); 
							}			
						} else { 
							int rsValueCoded=rs1.getInt("value_coded");
							if(!rs1.wasNull()) {				
								if(widgetType.equalsIgnoreCase("ListBox-Code")) { /*retrieving the description of the concept as it includes the necessary code*/
									conceptNameStatement=con.prepareStatement("select description from openmrs.concept where concept_id=?");
									conceptNameStatement.setInt(1, rsValueCoded);
									rs=conceptNameStatement.executeQuery();
									while(rs.next()) { 
										finalObsValue=rs.getString("description"); 
									}
									if(finalObsValue.equalsIgnoreCase("null")||finalObsValue==null) {
										finalObsValue="no information";
									}
								} else { /*retrieving the name of the concept*/
									if(rsValueCoded==1065) { /*is used in boolean form fields, 1065=YES --> needs to be converted to "true"*/
										finalObsValue="true";
									} else if(rsValueCoded==1066) { /*1066=NO --> needs to be converted to "false" */
										finalObsValue="false";
									} else {
										conceptNameStatement=con.prepareStatement("select name from openmrs.concept_name where concept_id=?");
										conceptNameStatement.setInt(1, rsValueCoded);
										rs=conceptNameStatement.executeQuery();
										while(rs.next()) { 
											finalObsValue=rs.getString("name"); 
										}
									}
								}
							}			
						}
						java.sql.Timestamp rsValueDateTime=rs1.getTimestamp("value_datetime");
						if(!rs1.wasNull()) {
							finalObsValue=String.valueOf(rsValueDateTime);
						}
						double rsValueNumeric=rs1.getDouble("value_numeric");
						if(!rs1.wasNull()) {
							finalObsValue=String.valueOf(rsValueNumeric);
						}
						String rsValueText=rs1.getString("value_text");
						if(!rs1.wasNull()) {
							finalObsValue=rsValueText;
						}
					}

					/*looking for a default value in case finalsObsValue is still null*/
					String rsValueDefault=rs1.getString("default_value");
					if(!rs1.wasNull() && finalObsValue==null) {
						finalObsValue=rsValueDefault;
					}
					
					/*path and value are added to HashMap*/
					if(finalObsValue==null) {
						pathsAndObs.put(rsPath, "");
					} else {
						pathsAndObs.put(rsPath, finalObsValue);
					}
				}	
			} while(rs1.next());	
			
		} catch(SQLException exc) {
			System.out.println("SQLException: " + exc.getMessage());
			System.out.println("SQLState: " + exc.getSQLState());
			System.out.println("VendorError: " + exc.getErrorCode());	
			exc.printStackTrace();
			
		} finally {
	        try {
	            if (encounterStatement != null) {
	            	encounterStatement.close();
	            }
	            if (dataStatement != null) {
	            	dataStatement.close();
	            }
	            if (conceptNameStatement != null) {
	            	conceptNameStatement.close();
	            }
	            if (rs != null) {
	            	rs.close();
	            }
	            if (rs1 != null) {
	            	rs1.close();
	            }
	        } catch (SQLException sqle) {
	        	System.out.println("SQLException: " + sqle.getMessage());
	        }
		}				
		return pathsAndObs;
	}
}
