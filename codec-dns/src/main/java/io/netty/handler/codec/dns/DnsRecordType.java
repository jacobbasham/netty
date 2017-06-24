/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.internal.UnstableApi;
import java.util.ArrayList;
import java.util.Collections;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents a DNS record type.
 */
@UnstableApi
public final class DnsRecordType implements Comparable<DnsRecordType> {

    /**
     * Address record RFC 1035 Returns a 32-bit IPv4 address, most commonly used
     * to map hostnames to an IP address of the host, but also used for DNSBLs,
     * storing subnet masks in RFC 1101, etc.
     */
    public static final DnsRecordType A = new DnsRecordType(0x0001, "A");

    /**
     * Name server record RFC 1035 Delegates a DNS zone to use the given
     * authoritative name servers
     */
    public static final DnsRecordType NS = new DnsRecordType(0x0002, "NS");

    /**
     * Canonical name record RFC 1035 Alias of one name to another: the DNS
     * lookup will continue by retrying the lookup with the new name.
     */
    public static final DnsRecordType CNAME = new DnsRecordType(0x0005, "CNAME");

    /**
     * Start of [a zone of] authority record RFC 1035 and RFC 2308 Specifies
     * authoritative information about a DNS zone, including the primary name
     * server, the email of the domain administrator, the domain serial number,
     * and several timers relating to refreshing the zone.
     */
    public static final DnsRecordType SOA = new DnsRecordType(0x0006, "SOA");

    /**
     * Pointer record RFC 1035 Pointer to a canonical name. Unlike a CNAME, DNS
     * processing does NOT proceed, just the name is returned. The most common
     * use is for implementing reverse DNS lookups, but other uses include such
     * things as DNS-SD.
     */
    public static final DnsRecordType PTR = new DnsRecordType(0x000c, "PTR");

    /**
     * Mail exchange record RFC 1035 Maps a domain name to a list of message
     * transfer agents for that domain.
     */
    public static final DnsRecordType MX = new DnsRecordType(0x000f, "MX");

    /**
     * Text record RFC 1035 Originally for arbitrary human-readable text in a
     * DNS record. Since the early 1990s, however, this record more often
     * carries machine-readable data, such as specified by RFC 1464,
     * opportunistic encryption, Sender Policy Framework, DKIM, DMARC DNS-SD,
     * etc.
     */
    public static final DnsRecordType TXT = new DnsRecordType(0x0010, "TXT");

    /**
     * Responsible person record RFC 1183 Information about the responsible
     * person(s) for the domain. Usually an email address with the @ replaced by
     * a .
     */
    public static final DnsRecordType RP = new DnsRecordType(0x0011, "RP");

    /**
     * AFS database record RFC 1183 Location of database servers of an AFS cell.
     * This record is commonly used by AFS clients to contact AFS cells outside
     * their local domain. A subtype of this record is used by the obsolete
     * DCE/DFS file system.
     */
    public static final DnsRecordType AFSDB = new DnsRecordType(0x0012, "AFSDB");

    /**
     * Signature record RFC 2535 Signature record used in SIG(0) (RFC 2931) and
     * TKEY (RFC 2930). RFC 3755 designated RRSIG as the replacement for SIG for
     * use within DNSSEC.
     */
    public static final DnsRecordType SIG = new DnsRecordType(0x0018, "SIG");

    /**
     * key record RFC 2535 and RFC 2930 Used only for SIG(0) (RFC 2931) and TKEY
     * (RFC 2930). RFC 3445 eliminated their use for application keys and
     * limited their use to DNSSEC. RFC 3755 designates DNSKEY as the
     * replacement within DNSSEC. RFC 4025 designates IPSECKEY as the
     * replacement for use with IPsec.
     */
    public static final DnsRecordType KEY = new DnsRecordType(0x0019, "KEY");

    /**
     * IPv6 address record RFC 3596 Returns a 128-bit IPv6 address, most
     * commonly used to map hostnames to an IP address of the host.
     */
    public static final DnsRecordType AAAA = new DnsRecordType(0x001c, "AAAA");

    /**
     * Location record RFC 1876 Specifies a geographical location associated
     * with a domain name.
     */
    public static final DnsRecordType LOC = new DnsRecordType(0x001d, "LOC");

    /**
     * Service locator RFC 2782 Generalized service location record, used for
     * newer protocols instead of creating protocol-specific records such as MX.
     */
    public static final DnsRecordType SRV = new DnsRecordType(0x0021, "SRV");

    /**
     * Naming Authority Pointer record RFC 3403 Allows regular expression based
     * rewriting of domain names which can then be used as URIs, further domain
     * names to lookups, etc.
     */
    public static final DnsRecordType NAPTR = new DnsRecordType(0x0023, "NAPTR");

    /**
     * Key eXchanger record RFC 2230 Used with some cryptographic systems (not
     * including DNSSEC) to identify a key management agent for the associated
     * domain-name. Note that this has nothing to do with DNS Security. It is
     * Informational status, rather than being on the IETF standards-track. It
     * has always had limited deployment, but is still in use.
     */
    public static final DnsRecordType KX = new DnsRecordType(0x0024, "KX");

    /**
     * Certificate record RFC 4398 Stores PKIX, SPKI, PGP, etc.
     */
    public static final DnsRecordType CERT = new DnsRecordType(0x0025, "CERT");

    /**
     * Delegation name record RFC 2672 DNAME creates an alias for a name and all
     * its subnames, unlike CNAME, which aliases only the exact name in its
     * label. Like the CNAME record, the DNS lookup will continue by retrying
     * the lookup with the new name.
     */
    public static final DnsRecordType DNAME = new DnsRecordType(0x0027, "DNAME");

    /**
     * Option record RFC 2671 This is a pseudo DNS record type needed to support
     * EDNS.
     */
    public static final DnsRecordType OPT = new DnsRecordType(0x0029, "OPT");

    /**
     * Address Prefix List record RFC 3123 Specify lists of address ranges, e.g.
     * in CIDR format, for various address families. Experimental.
     */
    public static final DnsRecordType APL = new DnsRecordType(0x002a, "APL");

    /**
     * Delegation signer record RFC 4034 The record used to identify the DNSSEC
     * signing key of a delegated zone.
     */
    public static final DnsRecordType DS = new DnsRecordType(0x002b, "DS");

    /**
     * SSH Public Key Fingerprint record RFC 4255 Resource record for publishing
     * SSH public host key fingerprints in the DNS System, in order to aid in
     * verifying the authenticity of the host. RFC 6594 defines ECC SSH keys and
     * SHA-256 hashes. See the IANA SSHFP RR parameters registry for details.
     */
    public static final DnsRecordType SSHFP = new DnsRecordType(0x002c, "SSHFP");

    /**
     * IPsec Key record RFC 4025 Key record that can be used with IPsec.
     */
    public static final DnsRecordType IPSECKEY = new DnsRecordType(0x002d, "IPSECKEY");

    /**
     * DNSSEC signature record RFC 4034 Signature for a DNSSEC-secured record
     * set. Uses the same format as the SIG record.
     */
    public static final DnsRecordType RRSIG = new DnsRecordType(0x002e, "RRSIG");

    /**
     * Next-Secure record RFC 4034 Part of DNSSEC, used to prove a name does not
     * exist. Uses the same format as the (obsolete) NXT record.
     */
    public static final DnsRecordType NSEC = new DnsRecordType(0x002f, "NSEC");

    /**
     * DNS Key record RFC 4034 The key record used in DNSSEC. Uses the same
     * format as the KEY record.
     */
    public static final DnsRecordType DNSKEY = new DnsRecordType(0x0030, "DNSKEY");

    /**
     * DHCP identifier record RFC 4701 Used in conjunction with the FQDN option
     * to DHCP.
     */
    public static final DnsRecordType DHCID = new DnsRecordType(0x0031, "DHCID");

    /**
     * NSEC record version 3 RFC 5155 An extension to DNSSEC that allows proof
     * of nonexistence for a name without permitting zonewalking.
     */
    public static final DnsRecordType NSEC3 = new DnsRecordType(0x0032, "NSEC3");

    /**
     * NSEC3 parameters record RFC 5155 Parameter record for use with NSEC3.
     */
    public static final DnsRecordType NSEC3PARAM = new DnsRecordType(0x0033, "NSEC3PARAM");

    /**
     * TLSA certificate association record RFC 6698 A record for DNS-based
     * Authentication of Named Entities (DANE). RFC 6698 defines The TLSA DNS
     * resource record is used to associate a TLS server certificate or public
     * key with the domain name where the record is found, thus forming a 'TLSA
     * certificate association'.
     */
    public static final DnsRecordType TLSA = new DnsRecordType(0x0034, "TLSA");

    /**
     * Host Identity Protocol record RFC 5205 Method of separating the end-point
     * identifier and locator roles of IP addresses.
     */
    public static final DnsRecordType HIP = new DnsRecordType(0x0037, "HIP");

    /**
     * Sender Policy Framework record RFC 4408 Specified as part of the SPF
     * protocol as an alternative to of storing SPF data in TXT records. Uses
     * the same format as the earlier TXT record.
     */
    public static final DnsRecordType SPF = new DnsRecordType(0x0063, "SPF");

    /**
     * Secret key record RFC 2930 A method of providing keying material to be
     * used with TSIG that is encrypted under the public key in an accompanying
     * KEY RR..
     */
    public static final DnsRecordType TKEY = new DnsRecordType(0x00f9, "TKEY");

    /**
     * Transaction Signature record RFC 2845 Can be used to authenticate dynamic
     * updates as coming from an approved client, or to authenticate responses
     * as coming from an approved recursive name server similar to DNSSEC.
     */
    public static final DnsRecordType TSIG = new DnsRecordType(0x00fa, "TSIG");

    /**
     * Incremental Zone Transfer record RFC 1996 Requests a zone transfer of the
     * given zone but only differences from a previous serial number. This
     * request may be ignored and a full (AXFR) sent in response if the
     * authoritative server is unable to fulfill the request due to
     * configuration or lack of required deltas.
     */
    public static final DnsRecordType IXFR = new DnsRecordType(0x00fb, "IXFR");

    /**
     * Authoritative Zone Transfer record RFC 1035 Transfer entire zone file
     * from the master name server to secondary name servers.
     */
    public static final DnsRecordType AXFR = new DnsRecordType(0x00fc, "AXFR");

    /**
     * A question record type - request mailbox-related info. Obsolete but needed to implement NSEC completely.
     */
    public static final DnsRecordType MAILB = new DnsRecordType(0x00fd, "MAILB");

    /**
     * A question record type - request for a mail agent. Obsolete, but needed to implement NSEC completely.
     */
    public static final DnsRecordType MAILA = new DnsRecordType(0x00fe, "MAILA");

    /**
     * All cached records RFC 1035 Returns all records of all types known to the
     * name server. If the name server does not have any information on the
     * name, the request will be forwarded on. The records returned may not be
     * complete. For example, if there is both an A and an MX for a name, but
     * the name server has only the A record cached, only the A record will be
     * returned. Sometimes referred to as ANY, for example in Windows nslookup
     * and Wireshark.
     */
    public static final DnsRecordType ANY = new DnsRecordType(0x00ff, "*");

    /**
     * Certification Authority Authorization record RFC 6844 CA pinning,
     * constraining acceptable CAs for a host/domain.
     */
    public static final DnsRecordType CAA = new DnsRecordType(0x0101, "CAA");

    /**
     * URI record type described in
     * <a href="http://www.rfc-editor.org/rfc/rfc7553.txt">RFC 7553</a>.
     */
    public static final DnsRecordType URI = new DnsRecordType(0x0100, "URI");

    /**
     * DNSSEC Trust Authorities record N/A Part of a deployment proposal for
     * DNSSEC without a signed DNS root. See the IANA database and Weiler Spec
     * for details. Uses the same format as the DS record.
     */
    public static final DnsRecordType TA = new DnsRecordType(0x8000, "TA");

    /**
     * DNSSEC Lookaside Validation record RFC 4431 For publishing DNSSEC trust
     * anchors outside of the DNS delegation chain. Uses the same format as the
     * DS record. RFC 5074 describes a way of using these records.
     */
    public static final DnsRecordType DLV = new DnsRecordType(0x8001, "DLV");

    private static final Map<String, DnsRecordType> BY_NAME;
    private static final IntObjectHashMap<DnsRecordType> BY_TYPE;

    static {
        DnsRecordType[] all = {
            A, NS, CNAME, SOA, PTR, MX, TXT, RP, AFSDB, SIG, KEY, AAAA, LOC, SRV, NAPTR, KX, CERT, DNAME, OPT, APL,
            DS, SSHFP, IPSECKEY, RRSIG, NSEC, DNSKEY, DHCID, NSEC3, NSEC3PARAM, TLSA, HIP, SPF, TKEY, TSIG, IXFR,
            AXFR, ANY, CAA, TA, DLV, MAILA, MAILB, URI
        };

        BY_NAME = new HashMap<String, DnsRecordType>(all.length);
        BY_TYPE = new IntObjectHashMap<DnsRecordType>(all.length);

        for (DnsRecordType type: all) {
            BY_NAME.put(type.name(), type);
            BY_TYPE.put(type.intValue(), type);
        }
    }

    /**
     * Look up a record type by number.  Returns a constant value if the
     * number is a known type; otherwise returns a newly created type.
     */
    public static DnsRecordType valueOf(int intValue) {
        if (intValue > 1 << 16) {
            throw new IllegalArgumentException("> 2 byte value: " + Integer.toHexString(intValue));
        }
        DnsRecordType result = BY_TYPE.get(intValue);
        if (result == null) {
            return new DnsRecordType(intValue);
        }
        return result;
    }

    /**
     * Returns true if this record type is defined in an RFC and implemented
     * as a constant on this class.
     */
    public boolean isStandardType() {
        return BY_TYPE.containsKey(intValue);
    }

    /**
     * Look up a constant by name.
     *
     * @throws IllegalArgumentException if the name is not known
     */
    public static DnsRecordType valueOf(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        DnsRecordType result = BY_NAME.get(name);
        if (result == null) {
            StringBuilder sb = new StringBuilder("name: ").append(name).append(" (expected one of ");
            List<String> names = new ArrayList<String>(BY_NAME.keySet());
            Collections.sort(names);
            for (Iterator<String> it = names.iterator(); it.hasNext();) {
                String existingName = it.next();
                sb.append('"').append(existingName).append('"');
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            throw new IllegalArgumentException(sb.toString());
        }
        return result;
    }

    private final int intValue;
    private final String name;

    private DnsRecordType(int intValue) {
        this(intValue, "TYPE" + intValue);
    }

    private DnsRecordType(int intValue, String name) {
        if ((intValue & 0xffff) != intValue) {
            throw new IllegalArgumentException("intValue: " + intValue + " (expected: 0 ~ 65535)");
        }
        this.intValue = intValue;
        this.name = name;
    }

    /**
     * Returns the name of this type, as seen in bind config files
     */
    public String name() {
        return name;
    }

    /**
     * Returns the value of this DnsType as it appears in DNS protocol
     */
    public int intValue() {
        return intValue;
    }

    /**
     * Returns true if this type is a meta-type according to
     * <a href="https://tools.ietf.org/html/rfc2929#section-3.1">RFC 2929</a>.
     */
    public boolean isMetaType() {
        return equals(OPT) || equals(TSIG) || equals(TKEY);
    }

    /**
     * Returns true if this type is a question-type according to
     * <a href="https://tools.ietf.org/html/rfc2929#section-3.1">RFC 2929</a>.
     */
    public boolean isQType() {
        return equals(ANY) || equals(MAILA) || equals(MAILB) || equals(AXFR) || equals(IXFR);
    }

    /**
     * Returns true if this type is a meta-type question-type according to
     * <a href="https://tools.ietf.org/html/rfc2929#section-3.1">RFC 2929</a>,
     * <i>also checking if the integer value is with the 128-255 reserved range for new meta types and question
     * types</i>.  Used in particular for determining if a type is legal in an NSEC record.
     */
    public boolean isMetaTypeOrQType() {
        return (intValue >= 128 && intValue <= 255) || isMetaType() || isQType();
    }

    @Override
    public int hashCode() {
        return intValue * 73;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DnsRecordType && ((DnsRecordType) o).intValue == intValue;
    }

    @Override
    public int compareTo(DnsRecordType o) {
        return intValue == o.intValue ? 0 : intValue < o.intValue ? -1 : 1;
    }

    @Override
    public String toString() {
        return name;
    }
}
