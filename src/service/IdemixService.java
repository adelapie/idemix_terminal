/**
 * IdemixService.java
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) Pim Vullers, Radboud University Nijmegen, June 2011.
 */

package service;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.Vector;

import net.sourceforge.scuba.smartcards.CardService;
import net.sourceforge.scuba.smartcards.CardServiceException;
import net.sourceforge.scuba.smartcards.CommandAPDU;
import net.sourceforge.scuba.smartcards.ICommandAPDU;
import net.sourceforge.scuba.smartcards.IResponseAPDU;
import net.sourceforge.scuba.smartcards.ISO7816;
import net.sourceforge.scuba.util.Hex;

import com.ibm.zurich.idmx.api.ProverInterface;
import com.ibm.zurich.idmx.api.RecipientInterface;
import com.ibm.zurich.idmx.dm.Credential;
import com.ibm.zurich.idmx.dm.Values;
import com.ibm.zurich.idmx.dm.structure.AttributeStructure;
import com.ibm.zurich.idmx.dm.structure.CredentialStructure;
import com.ibm.zurich.idmx.issuance.IssuanceSpec;
import com.ibm.zurich.idmx.issuance.Message;
import com.ibm.zurich.idmx.issuance.Message.IssuanceProtocolValues;
import com.ibm.zurich.idmx.key.IssuerPublicKey;
import com.ibm.zurich.idmx.showproof.Identifier;
import com.ibm.zurich.idmx.showproof.Proof;
import com.ibm.zurich.idmx.showproof.ProofSpec;
import com.ibm.zurich.idmx.showproof.predicates.CLPredicate;
import com.ibm.zurich.idmx.showproof.predicates.Predicate;
import com.ibm.zurich.idmx.showproof.predicates.Predicate.PredicateType;
import com.ibm.zurich.idmx.showproof.sval.SValue;
import com.ibm.zurich.idmx.showproof.sval.SValuesProveCL;
import com.ibm.zurich.idmx.utils.StructureStore;
import com.ibm.zurich.idmx.utils.SystemParameters;

/**
 * Idemix Smart Card Interface based on a SCUBA Card Service.
 *  
 * @author Pim Vullers
 * @version $Revision: 554 $ by $Author: pim $
 *          $LastChangedDate: 2011-04-28 16:31:47 +0200 (Thu, 28 Apr 2011) $
 */
public class IdemixService extends CardService implements ProverInterface, RecipientInterface {
    /**
     * Control the amount of output generated by this class.
     */
    private static final boolean VERBOSE = true;

    /**
     * Universal version identifier to match versions during deserialisation.
     */
    private static final long serialVersionUID = -6317383635196413L;

    /**
     * AID of the Idemix applet: ASCII encoding of "idemix".
     */
    private static final byte[] AID = {0x69, 0x64, 0x65, 0x6D, 0x69, 0x78};

    /**
     * INStruction to select an applet.
     */
    private static final byte INS_SELECT = (byte) 0xA4;

    /**
     * P1 parameter for select by name.
     */
    private static final byte P1_SELECT = 0x04;

    /**
     * CLAss to be used for Idemix APDUs.
     */
    private static final byte CLA_IDEMIX = (byte) 0x80;

    /**
     * INStruction to select a credential on the card.
     */
    @SuppressWarnings("unused")
	private static final byte INS_SELECT_CREDENTIAL = 0x00;
    
    /**
     * INStruction to generate the master secret on the card.
     */
    private static final byte INS_GENERATE_SECRET = 0x01;

    /**
     * INStruction to start issuing a credential (and to set the corresponding
     * context).
     */
    private static final byte INS_ISSUE_CREDENTIAL = 0x10;

    /**
     * INStruction to issue the n value from the issuer public key. 
     */
    private static final byte INS_ISSUE_PUBLIC_KEY_N = 0x11;

    /**
     * INStruction to issue the z value from the issuer public key. 
     */
    private static final byte INS_ISSUE_PUBLIC_KEY_Z = 0x12;
    
    /**
     * INStruction to issue the s value from the issuer public key. 
     */
    private static final byte INS_ISSUE_PUBLIC_KEY_S = 0x13;
    
    /**
     * INStruction to issue the R values from the issuer public key. 
     */
    private static final byte INS_ISSUE_PUBLIC_KEY_R = 0x14;
    
    /**
     * INStruction to issue the attributes. 
     */
    private static final byte INS_ISSUE_ATTRIBUTES = 0x15;

    /**
     * INStruction to send the first nonce (n_1) and compute/receive the 
     * combined hidden attributes (U).
     */
    private static final byte INS_ISSUE_NONCE_1 = 0x16;

    /**
     * INStruction to receive the zero-knowledge proof for correct construction 
     * of U (c, v^', s_A). 
     */
    private static final byte INS_ISSUE_PROOF_U = 0x17;

    /**
     * INStruction to receive the second nonce (n_2). 
     */
    private static final byte INS_ISSUE_NONCE_2 = 0x18;

    /**
     * INStruction to send the blind signature (A, e, v''). 
     */
    private static final byte INS_ISSUE_SIGNATURE = 0x19;

    /**
     * INStruction to send the zero-knowledge proof for correct construction of 
     * the signature (s_e, c'). 
     */
    private static final byte INS_ISSUE_PROOF_A = 0x1A;

    /**
     * INStruction to start proving aattributes from a credential (and to set 
     * the corresponding context).
     */
    private static final byte INS_PROVE_CREDENTIAL = 0x20;

    /**
     * INStruction to send the list of attributes to be disclosed.
     */
    private static final byte INS_PROVE_SELECTION = 0x21;

    /**
     * INStruction to send the challenge (m) to be signed in the proof and 
     * receive the commitment for the proof (a). 
     */
    private static final byte INS_PROVE_NONCE = 0x22;

    /**
     * INStruction to receive the values A', e^ and v^. 
     */
    private static final byte INS_PROVE_SIGNATURE = 0x23;

    /**
     * INStruction to receive the disclosed attributes (A_i).
     */
    private static final byte INS_PROVE_ATTRIBUTE = 0x24;

    /**
     * INStruction to receive the values for the undisclosed attributes (r_i). 
     */
    private static final byte INS_PROVE_RESPONSE = 0x25;

    /**
     * P1 parameter for the PROOF_U data instructions. 
     */
    private static final byte P1_PROOF_U_C = 0x00;
    private static final byte P1_PROOF_U_VPRIMEHAT = 0x01;
    private static final byte P1_PROOF_U_S_A = 0x02;

    /**
     * P1 parameters for SIGNATURE data instructions.
     */
    private static final byte P1_SIGNATURE_A = 0x00;
    private static final byte P1_SIGNATURE_E = 0x01;
    private static final byte P1_SIGNATURE_V = 0x02;
    private static final byte P1_SIGNATURE_VERIFY = 0x03;

    /**
     * P1 parameters for PROOF_A data instructions. 
     */
    private static final byte P1_PROOF_A_C = 0x00;
    private static final byte P1_PROOF_A_S_E = 0x01;
    private static final byte P1_PROOF_A_VERIFY = 0x02;

    /**
     * SCUBA service to communicate with the card.
     */
    protected CardService service;

    /**
     * Keep the systemParameters around for length checks
     */
    protected SystemParameters sysPars;

    /**************************************************************************/
    /* SCUBA / Smart Card Setup                                               */
    /**************************************************************************/

    /**
     * Construct a new Idemix service based on some CardService, which will be
     * used for the actual communication with an Idemix applet.
     * 
     * @param service the service to use for communication with the applet.
     */
    public IdemixService(CardService service) {
        this.service = service;
    }

    /**
     * Open a communication channel to an Idemix applet.
     */
    public void open() 
    throws CardServiceException {
        if (!isOpen()) {
            service.open();
        }
        selectApplet();
    }

    /**
     * Check whether a communication channel with a smart card exists.
     * 
     * @return whether the channel is open or not.
     */
    public boolean isOpen() {
        return service.isOpen();
    }

    /**
     * Send an APDU over the communication channel to the smart card.
     * 
     * @param apdu the APDU to be send to the smart card.
     * @return ResponseAPDU the response from the smart card.
     * @throws CardServiceException if some error occurred while transmitting.
     */
    public IResponseAPDU transmit(ICommandAPDU capdu) 
    throws CardServiceException { 

        if (VERBOSE) {
            System.out.println();
            System.out.println("C: " + Hex.bytesToHexString(capdu.getBytes()));    		
        }

        long start = System.nanoTime();
        IResponseAPDU rapdu = service.transmit(capdu);
        long duration = (System.nanoTime() - start)/1000000;

        if (VERBOSE) {
            System.out.println(" duration: " + duration + " ms");
            System.out.println("R: " + Hex.bytesToHexString(rapdu.getBytes()));
        }

        return rapdu;
    }

    /**
     * Close the communication channel with the Idemix applet.
     */
    public void close() {
        if (service != null) {
            service.close();
        }
    }

    /**
     * Select the Idemix applet on the smart card.
     * 
     * @throws CardServiceException if an error occurred.
     */
    public void selectApplet() 
    throws CardServiceException {
        CommandAPDU select = new CommandAPDU(ISO7816.CLA_ISO7816,  
                INS_SELECT, P1_SELECT, 0x00, AID, 256); // LE == 0 is required.
        IResponseAPDU response = transmit(select);
        if (response.getSW() != 0x00009000) {
            throw new CardServiceException("Could not select the Idemix " +
                    "applet.", response.getSW());
        }
    }


    /**
     * Report about commands not supported by the smart card. 
     */
    protected void notSupported(String message) {
        System.err.println();
        System.err.println(message);
        System.err.println("This command is NOT supported by the smart card.");
        System.err.println();
    }

    /**
     * Produces an unsigned byte-array representation of a BigInteger.
     *
     * <p>BigInteger adds an extra sign bit to the beginning of its byte
     * array representation.  In some cases this will cause the size
     * of the byte array to increase, which may be unacceptable for some
     * applications. This function returns a minimal byte array representing
     * the BigInteger without extra sign bits.
     *
     * <p>This method is taken from the Network Security Services for Java (JSS)
     * currently maintained by the Mozilla Foundation and originally developed 
     * by the Netscape Communications Corporation.
     * 
     * @return unsigned big-endian byte array representation of a BigInteger.
     */
    public static byte[] BigIntegerToUnsignedByteArray(BigInteger big) {
        byte[] ret;

        // big must not be negative
        assert(big.signum() != -1);

        // bitLength is the size of the data without the sign bit.  If
        // it exactly fills an integral number of bytes, that means a whole
        // new byte will have to be added to accommodate the sign bit. In
        // this case we need to remove the first byte.
        if(big.bitLength() % 8 == 0) {
            byte[] array = big.toByteArray();
            // The first byte should just be sign bits
            assert( array[0] == 0 );
            ret = new byte[array.length-1];
            System.arraycopy(array, 1, ret, 0, ret.length);
        } else {
            ret = big.toByteArray();
        }
        return ret;
    }
    
    /**
     * Fix the length of array representation of BigIntegers put into the APDUs.
     * 
     * @param integer of which the length needs to be fixed.
     * @param the new length of the integer in bits
     * @return an array with a fixed length.
     */
    public static byte[] fixLength(BigInteger integer, int length_in_bits) {
        byte[] array = BigIntegerToUnsignedByteArray(integer);
        int length;
        
        length = length_in_bits/8;
        if (length_in_bits % 8 != 0){
        	length++;
        }
        
        assert (array.length <= length);
        
        int padding = length - array.length;
        byte[] fixed = new byte[length];
        Arrays.fill(fixed, (byte) 0x00);
        System.arraycopy(array, 0, fixed, padding, array.length);
        return fixed;
    }

    /**************************************************************************/
    /* Idemix Smart Card Interface                                           */
    /**************************************************************************/

    /**
     * Set the specification of a certificate issuance: 
     * 
     * <ul>
     *   <li> issuer public key, and 
     *   <li> context. 
     * </ul>
     * 
     * @param spec the specification to be set.
     * @throws CardServiceException if an error occurred.
     */
    public void setIssuanceSpecification(IssuanceSpec spec) 
    throws CardServiceException {
    	short id = 1; // FIXME: derive this id somehow
    	
    	// Set the system parameters for this protocol run
    	sysPars = spec.getPublicKey().getGroupParams().getSystemParams();
        
        // Set the issuance context
        startIssuance(id, spec.getContext());
        
    	// Set the issuer public key
    	setPublicKey(spec.getPublicKey(), 
        		spec.getCredentialStructure().getAttributeStructs().size() + 1);
    }
    
    /**
     * Set the Issuer's public key:
     * 
     * <pre>
     *   n, Z, S, R_0, ..., R_l
     * </pre>
     * 
     * @param pubKey the public key to be set.
     * @throws CardServiceException if an error occurred.
     */
    void setPublicKey(IssuerPublicKey pubKey, int pubKeyElements) 
    throws CardServiceException {
    	int l_n = sysPars.getL_n();
        CommandAPDU command_n = new CommandAPDU(
        		CLA_IDEMIX, INS_ISSUE_PUBLIC_KEY_N, 0x00, 0x00, 
                fixLength(pubKey.getN(), l_n));
        IResponseAPDU response_n = transmit(command_n);
        if (response_n.getSW() != 0x00009000) {
            if (response_n.getSW() == 0x00006D00) {
                notSupported("Could not set public key.");
            } else {
                throw new CardServiceException("Could not set public key " +
                        "modulus (n).", response_n.getSW());
            }
        }
        
        CommandAPDU command_z = new CommandAPDU(
        		CLA_IDEMIX, INS_ISSUE_PUBLIC_KEY_Z, 0x00, 0x00, 
                fixLength(pubKey.getCapZ(), l_n));
        IResponseAPDU response_z = transmit(command_z);
        if (response_z.getSW() != 0x00009000) {
            if (response_z.getSW() == 0x00006D00) {
                notSupported("Could not set public key.");
            } else {
                throw new CardServiceException("Could not set public key " +
                        "element (Z).", response_z.getSW());
            }
        }
        
        CommandAPDU command_s = new CommandAPDU(
        		CLA_IDEMIX, INS_ISSUE_PUBLIC_KEY_S, 0x00, 0x00, 
        		fixLength(pubKey.getCapS(), l_n));
        IResponseAPDU response_s = transmit(command_s);
        if (response_s.getSW() != 0x00009000) {
            if (response_s.getSW() == 0x00006D00) {
                notSupported("Could not set public key.");
            } else {
                throw new CardServiceException("Could not set public key " +
                        "modulus (n).", response_s.getSW());
            }
        }
        
        BigInteger[] pubKeyElement = pubKey.getCapR();
        for (int i = 0; i < pubKeyElements; i++) {
            CommandAPDU command = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_PUBLIC_KEY_R, i, 0x00, 
            		fixLength(pubKeyElement[i], l_n));
            IResponseAPDU response = transmit(command);
            if (response.getSW() != 0x00009000) {
                if (response.getSW() == 0x00006D00) {
                    notSupported("Could not set public key elements.");
                } else {
                    throw new CardServiceException("Could not set public key " +
                            "element (R@index " + i + ").", response.getSW());
                }
            }
        }
    }

    void startIssuance(short id, BigInteger context) 
    throws CardServiceException {
        CommandAPDU command = new CommandAPDU(
        		CLA_IDEMIX, INS_ISSUE_CREDENTIAL, id >> 8, id & 0xff, 
        		fixLength(context, sysPars.getL_H()));
        IResponseAPDU response = transmit(command);
        if (response.getSW() != 0x00009000) {
            if (response.getSW() == 0x00006D00) {
                notSupported("Could not start issuance.");
            } else if (response.getSW() == 0x00006986) {
            	throw new CardServiceException("Could not issue credential, already issued.");
            } else {
                throw new CardServiceException("Could not start issuance.", 
                        response.getSW());
            }
        }
    }

    void startProof(short id, BigInteger context) 
    throws CardServiceException {
        CommandAPDU command = new CommandAPDU(
        		CLA_IDEMIX, INS_PROVE_CREDENTIAL, id >> 8, id & 0xff, 
        		fixLength(context, sysPars.getL_H()));
        IResponseAPDU response = transmit(command);
        if (response.getSW() != 0x00009000) {
            if (response.getSW() == 0x00006D00) {
                notSupported("Could not start proving.");
            } else if (response.getSW() == 0x00006A88) {
            	throw new CardServiceException("Credential not found.");
            } else {
                throw new CardServiceException("Could not start proving.", 
                        response.getSW());
            }
        }
    }
    
    /**
     * Generate the master secret: 
     * 
     * <pre>
     *   m_0
     * </pre>
     * 
     * @throws CardServiceException if an error occurred.
     */
    public void generateMasterSecret() 
    throws CardServiceException {
        CommandAPDU command = new CommandAPDU(
        		CLA_IDEMIX, INS_GENERATE_SECRET, 0x00, 0x00);
        IResponseAPDU response = transmit(command);
        if (response.getSW() != 0x00009000) {
            if (response.getSW() == 0x00006D00) {
                notSupported("Could not generate master secret.");
            } else if (response.getSW() == 0x00006986) {
                System.err.println("Could not generate master secret, already set.");
            } else {
                throw new CardServiceException("Could not generate master secret.", 
                        response.getSW());
            }
        }
    }

    /**
     * Send the pin to the card
     *
     * @throws CardServiceException if an error occurred.
     */
    public void sendPin(byte[] pin)
    throws CardServiceException {
        CommandAPDU command = new CommandAPDU(
        		ISO7816.CLA_ISO7816, ISO7816.INS_VERIFY, 0x00, 0x00, pin);
        IResponseAPDU response = transmit(command);
        if (response.getSW() != 0x00009000) {
            throw new CardServiceException("Could not authorize using PIN.",
                    response.getSW());
        }
    }
    
    /**
     * Set the attributes: 
     * 
     * <pre>
     *   m_1, ..., m_l
     * </pre>
     * 
     * @param spec the issuance specification for the ordering of the values.
     * @param values the attributes to be set.
     * @throws CardServiceException if an error occurred.
     */
    public void setAttributes(IssuanceSpec spec, Values values) 
    throws CardServiceException {
        Vector<AttributeStructure> structs = spec.getCredentialStructure().getAttributeStructs();
        int i = 1;
        for (AttributeStructure struct : structs) {
            BigInteger attr = (BigInteger) values.get(struct.getName()).getContent();
            CommandAPDU command = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_ATTRIBUTES, i++, 0x00, 
                    fixLength(attr, sysPars.getL_m()));
            IResponseAPDU response = transmit(command);
            if (response.getSW() != 0x00009000) {
                if (response.getSW() == 0x00006D00) {
                    notSupported("Could not set attributes.");
                } else {                
                    throw new CardServiceException("Could not set attribute " +
                    		"(m@index " + (i + 1) + ").", response.getSW());
                }
            }
        }
    }
    
    /**
     * @param theNonce1
     *            Nonce provided by the verifier.
     * @return Message containing the proof about the hidden and committed
     *         attributes sent to the Issuer.
     */
    public Message round1(final Message msg) {
        HashMap<IssuanceProtocolValues, BigInteger> issuanceProtocolValues = 
            new HashMap<IssuanceProtocolValues, BigInteger>();
        HashMap<String, SValue> sValues = new HashMap<String, SValue>();
        TreeMap<String, BigInteger> additionalValues = 
            new TreeMap<String, BigInteger>();
        BigInteger theNonce1 = msg.getIssuanceElement(
                IssuanceProtocolValues.nonce_recipient);

        // Hide CardServiceExceptions, instead return null on failure
        try {
            // send Nonce and receive U 
            CommandAPDU nonce_n1 = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_NONCE_1, 0x00, 0x00, 
                    fixLength(theNonce1,sysPars.getL_Phi()));
            IResponseAPDU response_n1 = transmit(nonce_n1);
            if (response_n1.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue nonce n1.", 
                        response_n1.getSW());
            }
            issuanceProtocolValues.put(IssuanceProtocolValues.capU, 
                    new BigInteger(1, response_n1.getData()));        

            // receive Proof
            CommandAPDU proof_c = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_PROOF_U, P1_PROOF_U_C, 0x00);
            IResponseAPDU response_c = transmit(proof_c);
            if (response_c.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue proof c.", 
                        response_c.getSW());
            }
            BigInteger challenge = new BigInteger(1, response_c.getData());

            CommandAPDU proof_vHatPrime = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_PROOF_U, P1_PROOF_U_VPRIMEHAT, 0x00);
            IResponseAPDU response_vHatPrime = transmit(proof_vHatPrime);
            if (response_vHatPrime.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue proof v^'.", 
                        response_vHatPrime.getSW());
            }
            additionalValues.put(IssuanceSpec.vHatPrime, 
                    new BigInteger(1, response_vHatPrime.getData()));

            CommandAPDU proof_s_A = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_PROOF_U, P1_PROOF_U_S_A, 0x00);
            IResponseAPDU response_s_A = transmit(proof_s_A);
            if (response_s_A.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue proof s_A.", 
                        response_s_A.getSW());
            }
            sValues.put(IssuanceSpec.MASTER_SECRET_NAME,
                    new SValue(new BigInteger(1, response_s_A.getData())));

            // receive Nonce
            CommandAPDU nonce_n2 = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_NONCE_2, 0x00, 0x00);
            IResponseAPDU response_n2 = transmit(nonce_n2);
            if (response_n2.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue nonce n2.", 
                        response_n2.getSW());
            }
            issuanceProtocolValues.put(IssuanceProtocolValues.nonce_recipient, 
                    new BigInteger(1, response_n2.getData()));

            // Return the next protocol message
            return new Message(issuanceProtocolValues, 
                    new Proof(challenge, sValues, additionalValues));
            
        // Report caught exceptions
        } catch (CardServiceException e) {
            System.err.println(e.getMessage() + "\n");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Called with the second protocol flow as input, outputs the Credential.
     * This is the last step of the issuance protocol, where the Recipient
     * verifies that the signature is valid and outputs it.
     * 
     * @param msg
     *            the second flow of the protocol, a message from the Issuer
     * @return the Credential, if it's valid, null otherwise.
     */
    public Credential round3(final Message msg) {
        // Hide CardServiceExceptions, instead return null on failure
        try {
            // send Signature
            BigInteger A = msg.getIssuanceElement(IssuanceProtocolValues.capA);
            CommandAPDU signature_A = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_SIGNATURE, P1_SIGNATURE_A, 0x00, 
                    fixLength(A, sysPars.getL_n()));
            IResponseAPDU response_A = transmit(signature_A);
            if (response_A.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue signature A.", 
                        response_A.getSW());
            }

            BigInteger e = msg.getIssuanceElement(IssuanceProtocolValues.e);
            CommandAPDU signature_e = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_SIGNATURE, P1_SIGNATURE_E, 0x00, 
                    fixLength(e, sysPars.getL_e()));
            IResponseAPDU response_e = transmit(signature_e);
            if (response_e.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue signature e.", 
                        response_e.getSW());
            }

            BigInteger v = msg.getIssuanceElement(IssuanceProtocolValues.vPrimePrime);
            CommandAPDU signature_v = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_SIGNATURE, P1_SIGNATURE_V, 0x00, 
                    fixLength(v, sysPars.getL_v()));
            IResponseAPDU response_v = transmit(signature_v);
            if (response_v.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue signature v''.", 
                        response_v.getSW());
            }

            CommandAPDU signature_verify = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_SIGNATURE, P1_SIGNATURE_VERIFY, 0x00);
            IResponseAPDU response_verify = transmit(signature_verify);
            if (response_verify.getSW() != 0x00009000) {
                throw new CardServiceException("Could not verify issued signature.", 
                        response_verify.getSW());
            }
            
            // send Proof 
            BigInteger c = msg.getProof().getChallenge();
            CommandAPDU proof_c = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_PROOF_A, P1_PROOF_A_C, 0x00, 
                    fixLength(c, sysPars.getL_H()));
            IResponseAPDU response_c = transmit(proof_c);
            if (response_c.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue proof c'.", 
                        response_c.getSW());
            }
        
            BigInteger s_e = 
            		(BigInteger) msg.getProof().getSValue(IssuanceSpec.s_e).getValue();
            CommandAPDU proof_s_e = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_PROOF_A, P1_PROOF_A_S_E, 0x00, 
                    fixLength(s_e, sysPars.getL_n()));
            IResponseAPDU response_s_e = transmit(proof_s_e);
            if (response_s_e.getSW() != 0x00009000) {
                throw new CardServiceException("Could not issue proof s_e.", 
                        response_s_e.getSW());
            }

            CommandAPDU proof_verify = new CommandAPDU(
            		CLA_IDEMIX, INS_ISSUE_PROOF_A, P1_PROOF_A_VERIFY, 0x00);
            IResponseAPDU response_proof = transmit(proof_verify);
            if (response_proof.getSW() != 0x00009000) {
                throw new CardServiceException("Could not verify proof.", 
                        response_verify.getSW());
            }
            
            // Do NOT return the generated Idemix credential
            return null;
            
        // Report caught exceptions
        } catch (CardServiceException e) {
            System.err.println(e.getMessage() + "\n");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Builds an Identity mixer show-proof data structure, which can be passed
     * to the verifier for verification.
     * 
     * @return Identity mixer show-proof data structure.
     */
    public Proof buildProof(final BigInteger nonce, final ProofSpec spec) {
        HashMap<String, SValue> sValues = new HashMap<String, SValue>();
        TreeMap<String, BigInteger> commonList = 
            new TreeMap<String, BigInteger>();

        // Set the system parameters for this protocol run
        sysPars = spec.getGroupParams().getSystemParams();
        
        List<Integer> disclosed = new Vector<Integer>();        
    	short id = 1; // FIXME: derive this id somehow

        Predicate predicate = spec.getPredicates().firstElement();
        if (predicate.getPredicateType() != PredicateType.CL) {
            throw new RuntimeException("Unimplemented predicate.");
        }
        CLPredicate pred = ((CLPredicate) predicate);
        StructureStore store = StructureStore.getInstance();
        CredentialStructure cred = (CredentialStructure) store.get(
               pred.getCredStructLocation());

        for (AttributeStructure attribute : cred.getAttributeStructs()) {
            String attName = attribute.getName();
            Identifier identifier = pred.getIdentifier(attName);
            if (identifier.isRevealed()) {
                disclosed.add(attribute.getKeyIndex());
            }
        }
        
        // translate the List to a byte[] 
        Collections.sort(disclosed);
        byte[] D = new byte[disclosed.size()];
        for (int i = 0; i < disclosed.size(); i++) {
            D[i] = disclosed.get(i).byteValue();
        }

        // Hide CardServiceExceptions, instead return null on failure
        try {
            
            // Set the context for this proof
            startProof(id, spec.getContext());

            // send the attribute disclosure selection
            CommandAPDU disclosure_d = new CommandAPDU(
            		CLA_IDEMIX, INS_PROVE_SELECTION, 0x00, 0x00, D);
            IResponseAPDU response_d = transmit(disclosure_d);
            if (response_d.getSW() != 0x00009000) {
                throw new CardServiceException("Could not set attribute " +
                        "disclosure selection.", response_d.getSW());
            }

            // send and receive the challenge
            CommandAPDU challenge_c = new CommandAPDU(
            		CLA_IDEMIX, INS_PROVE_NONCE, 0x00, 0x00, 
                    fixLength(nonce, sysPars.getL_Phi()));
            IResponseAPDU response_c = transmit(challenge_c);
            if (response_c.getSW() != 0x00009000) {
                throw new CardServiceException("Could not set the challenge " +
                		"n1.", response_c.getSW());
            }
            BigInteger challenge = new BigInteger(1, response_c.getData());

            // receive the randomised signature
            CommandAPDU signature_A = new CommandAPDU(
            		CLA_IDEMIX, INS_PROVE_SIGNATURE, P1_SIGNATURE_A, 0x00);
            IResponseAPDU response_A = transmit(signature_A);
            if (response_A.getSW() != 0x00009000) {
                throw new CardServiceException("Could not get the random " +
                		"signature A'.", response_A.getSW());
            }
            commonList.put(pred.getTempCredName(),
            		new BigInteger(1, response_A.getData()));

            CommandAPDU signature_e = new CommandAPDU(
            		CLA_IDEMIX, INS_PROVE_SIGNATURE, P1_SIGNATURE_E, 0x00);
            IResponseAPDU response_e = transmit(signature_e);
            if (response_e.getSW() != 0x00009000) {
                throw new CardServiceException("Could not get the random " +
                		"signature e^.", response_e.getSW());
            }
            BigInteger e = new BigInteger(1, response_e.getData());

            CommandAPDU signature_v = new CommandAPDU(
            		CLA_IDEMIX, INS_PROVE_SIGNATURE, P1_SIGNATURE_V, 0x00);
            IResponseAPDU response_v = transmit(signature_v);
            if (response_v.getSW() != 0x00009000) {
                throw new CardServiceException("Could not get the random " +
                		"signature v^'.", response_v.getSW());
            }
            BigInteger v = new BigInteger(1, response_v.getData());

            sValues.put(pred.getTempCredName(), new SValue(new SValuesProveCL(e, v)));

            // receive the randomised master secret
            CommandAPDU command = new CommandAPDU(
            		CLA_IDEMIX, INS_PROVE_RESPONSE, 0x00, 0x00);
            IResponseAPDU response = transmit(command);
            if (response.getSW() != 0x00009000) {
                throw new CardServiceException("Could not get random " +
                        "value (@index 0).", response.getSW());
            }
            BigInteger s = new BigInteger(1, response.getData());
            sValues.put(IssuanceSpec.MASTER_SECRET_NAME, new SValue(s));

            // iterate over all the identifiers
            for (AttributeStructure attribute : cred.getAttributeStructs()) {
                String attName = attribute.getName();
                Identifier identifier = pred.getIdentifier(attName);
                int i = attribute.getKeyIndex();
                if (identifier.isRevealed()) {
                    command = new CommandAPDU(
                    		CLA_IDEMIX, INS_PROVE_ATTRIBUTE, i, 0x00);
                    response = transmit(command);
                    if (response.getSW() != 0x00009000) {
                        throw new CardServiceException("Could not get disclosed " +
                                "attribute (@index " + i + ").", response.getSW());
                    }
                    s = new BigInteger(1, response.getData());
                } else {
                    command = new CommandAPDU(
                    		CLA_IDEMIX, INS_PROVE_RESPONSE, i, 0x00);
                    response = transmit(command);
                    if (response.getSW() != 0x00009000) {
                        throw new CardServiceException("Could not get random " +
                                "value (@index " + i + ").", response.getSW());
                    }
                    s = new BigInteger(1, response.getData());
                }
                // note that identifier names must be unique
                sValues.put(identifier.getName(), new SValue(s));
            }

            // Return the generated proof, based on the proof specification
            return new Proof(challenge, sValues, commonList);
        
        // Report caught exceptions
        } catch (CardServiceException e) {
            System.err.println(e.getMessage() + "\n");
            e.printStackTrace();
            return null;
        }
    }
}
