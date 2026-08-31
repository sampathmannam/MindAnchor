# Program 2 v3 `featuresJson` data-dictionary erratum

Status: documentation erratum; the frozen `mindanchor-research-v3` contract is unchanged.

The v3 data dictionary describes `PASSIVE_DAILY_REVISIONS.featuresJson` as containing
"feature names, values and units." That description is inaccurate. The field contains a
canonical JSON object mapping eligible feature names directly to numeric values; units are
defined elsewhere by the feature contract and are not embedded in this JSON value.

The v3 description is immutable because it participates in the frozen dictionary and export
compatibility hashes. Changing the literal would make existing v3 artifacts fail their
integrity contract. Consumers must interpret the field according to the corrected meaning
above while preserving the original v3 text and hashes.

The next dictionary version (v4) must use this corrected description:

> Canonical eligible daily feature names mapped to numeric values.
