/**
 * Country geo helpers for the Financial Map dashboard card.
 *
 * Bridges the app's ISO 3166-1 alpha-2 country codes (used by institutions and
 * user settings) with the world-atlas TopoJSON (keyed by ISO numeric ids) so we
 * can place highlight dots on country centroids and reverse-geocode real-estate
 * coordinates to a country entirely offline.
 */

import { feature } from 'topojson-client';
import { geoCentroid, geoContains } from 'd3-geo';
import type { Feature, FeatureCollection, Geometry } from 'geojson';
import worldTopologyJson from 'world-atlas/countries-110m.json';
import { ALL_COUNTRIES } from './countryUtils';

/** The raw world-atlas TopoJSON, passed straight to react-simple-maps `<Geographies>`. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const WORLD_TOPOLOGY = worldTopologyJson as any;

/** ISO 3166-1 numeric id (world-atlas feature id) → ISO 3166-1 alpha-2 code. */
const NUMERIC_TO_ALPHA2: Record<string, string> = {
    '004': 'AF', '008': 'AL', '012': 'DZ', '024': 'AO', '031': 'AZ', '032': 'AR',
    '036': 'AU', '040': 'AT', '044': 'BS', '050': 'BD', '051': 'AM', '056': 'BE',
    '064': 'BT', '068': 'BO', '070': 'BA', '072': 'BW', '076': 'BR', '084': 'BZ',
    '090': 'SB', '096': 'BN', '100': 'BG', '104': 'MM', '108': 'BI', '112': 'BY',
    '116': 'KH', '120': 'CM', '124': 'CA', '140': 'CF', '144': 'LK', '148': 'TD',
    '152': 'CL', '156': 'CN', '158': 'TW', '170': 'CO', '178': 'CG', '180': 'CD',
    '188': 'CR', '191': 'HR', '192': 'CU', '196': 'CY', '203': 'CZ', '204': 'BJ',
    '208': 'DK', '214': 'DO', '218': 'EC', '222': 'SV', '226': 'GQ', '231': 'ET',
    '232': 'ER', '233': 'EE', '238': 'FK', '242': 'FJ', '246': 'FI', '250': 'FR',
    '260': 'TF', '262': 'DJ', '266': 'GA', '268': 'GE', '270': 'GM', '275': 'PS',
    '276': 'DE', '288': 'GH', '300': 'GR', '304': 'GL', '320': 'GT', '324': 'GN',
    '328': 'GY', '332': 'HT', '340': 'HN', '348': 'HU', '352': 'IS', '356': 'IN',
    '360': 'ID', '364': 'IR', '368': 'IQ', '372': 'IE', '376': 'IL', '380': 'IT',
    '384': 'CI', '388': 'JM', '392': 'JP', '398': 'KZ', '400': 'JO', '404': 'KE',
    '408': 'KP', '410': 'KR', '414': 'KW', '417': 'KG', '418': 'LA', '422': 'LB',
    '426': 'LS', '428': 'LV', '430': 'LR', '434': 'LY', '440': 'LT', '442': 'LU',
    '450': 'MG', '454': 'MW', '458': 'MY', '466': 'ML', '478': 'MR', '484': 'MX',
    '496': 'MN', '498': 'MD', '499': 'ME', '504': 'MA', '508': 'MZ', '512': 'OM',
    '516': 'NA', '524': 'NP', '528': 'NL', '540': 'NC', '548': 'VU', '554': 'NZ',
    '558': 'NI', '562': 'NE', '566': 'NG', '578': 'NO', '586': 'PK', '591': 'PA',
    '598': 'PG', '600': 'PY', '604': 'PE', '608': 'PH', '616': 'PL', '620': 'PT',
    '624': 'GW', '626': 'TL', '630': 'PR', '634': 'QA', '642': 'RO', '643': 'RU',
    '646': 'RW', '682': 'SA', '686': 'SN', '688': 'RS', '694': 'SL', '703': 'SK',
    '704': 'VN', '705': 'SI', '706': 'SO', '710': 'ZA', '716': 'ZW', '724': 'ES',
    '728': 'SS', '729': 'SD', '732': 'EH', '740': 'SR', '748': 'SZ', '752': 'SE',
    '756': 'CH', '760': 'SY', '762': 'TJ', '764': 'TH', '768': 'TG', '780': 'TT',
    '784': 'AE', '788': 'TN', '792': 'TR', '795': 'TM', '800': 'UG', '804': 'UA',
    '807': 'MK', '818': 'EG', '826': 'GB', '834': 'TZ', '840': 'US', '854': 'BF',
    '858': 'UY', '860': 'UZ', '862': 'VE', '887': 'YE', '894': 'ZM',
};

type WorldFeature = Feature<Geometry, { name: string }>;

const FEATURES: WorldFeature[] = (
    feature(WORLD_TOPOLOGY, WORLD_TOPOLOGY.objects.countries) as unknown as FeatureCollection<
        Geometry,
        { name: string }
    >
).features as WorldFeature[];

/** Maps an alpha-2 code to its world-atlas feature (for centroid computation). */
const featureByAlpha2 = new Map<string, WorldFeature>();
for (const f of FEATURES) {
    const alpha2 = NUMERIC_TO_ALPHA2[String(f.id)];
    if (alpha2 && !featureByAlpha2.has(alpha2)) featureByAlpha2.set(alpha2, f);
}

const centroidCache = new Map<string, [number, number] | null>();

/** Returns the `[longitude, latitude]` centroid of a country, or null if unknown. */
export function countryCentroid(alpha2: string): [number, number] | null {
    const code = alpha2.toUpperCase();
    const cached = centroidCache.get(code);
    if (cached !== undefined) return cached;
    const feat = featureByAlpha2.get(code);
    const centroid = feat ? (geoCentroid(feat) as [number, number]) : null;
    centroidCache.set(code, centroid);
    return centroid;
}

/**
 * Reverse-geocodes a `[longitude, latitude]` point to an ISO alpha-2 country code
 * using offline point-in-polygon tests against the world-atlas geometries.
 */
export function countryOfPoint(longitude: number, latitude: number): string | undefined {
    for (const f of FEATURES) {
        if (geoContains(f, [longitude, latitude])) {
            return NUMERIC_TO_ALPHA2[String(f.id)];
        }
    }
    return undefined;
}

/** Maps a world-atlas ISO numeric id (feature id) to an ISO alpha-2 code, if known. */
export function alpha2FromNumericId(id: string | number): string | undefined {
    return NUMERIC_TO_ALPHA2[String(id)];
}

// ── Name / address resolution ──────────────────────────────────────────────

const normalize = (value: string): string =>
    value
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, ' ')
        .trim();

const nameToAlpha2 = new Map<string, string>();
const codeSet = new Set<string>();
for (const { code, name } of ALL_COUNTRIES) {
    nameToAlpha2.set(normalize(name), code);
    codeSet.add(code);
}
// A few common aliases not present in ALL_COUNTRIES canonical names.
const NAME_ALIASES: Record<string, string> = {
    'usa': 'US',
    'united states of america': 'US',
    'uk': 'GB',
    'great britain': 'GB',
    'england': 'GB',
    'scotland': 'GB',
    'wales': 'GB',
    'northern ireland': 'GB',
    'south korea': 'KR',
    'north korea': 'KP',
    'ivory coast': 'CI',
    'czechia': 'CZ',
    'uae': 'AE',
    'holland': 'NL',
};
for (const [alias, code] of Object.entries(NAME_ALIASES)) {
    nameToAlpha2.set(normalize(alias), code);
}

/** Human-readable country name for an alpha-2 code, falling back to the code itself. */
export function countryDisplayName(alpha2: string): string {
    const code = alpha2.toUpperCase();
    return ALL_COUNTRIES.find((c) => c.code === code)?.name ?? code;
}

/**
 * Best-effort extraction of an ISO alpha-2 country code from a free-form address.
 * Prefers a match in the trailing address segment (where the country usually sits)
 * before scanning the whole string. Returns undefined when no country is found.
 */
export function countryFromAddress(address?: string | null): string | undefined {
    if (!address) return undefined;
    const segments = address
        .split(/[\n,]/)
        .map((s) => s.trim())
        .filter(Boolean)
        .reverse();

    for (const segment of segments) {
        const normalized = normalize(segment);
        if (!normalized) continue;
        const direct = nameToAlpha2.get(normalized);
        if (direct) return direct;
        // Bare 2-letter country code as the last token (e.g. "…, FR").
        const upper = segment.trim().toUpperCase();
        if (/^[A-Z]{2}$/.test(upper) && codeSet.has(upper)) return upper;
    }

    // Fall back to scanning the whole address for any country name.
    const whole = normalize(address);
    for (const [name, code] of nameToAlpha2) {
        if (name.length >= 4 && whole.includes(name)) return code;
    }
    return undefined;
}
