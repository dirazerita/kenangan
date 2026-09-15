package id.kenang.core.data.story

/**
 * The verdict of the crop check (owner 2026-09-15). Before a face crop is
 * handed to a paid model as "this is X's face", a cheap vision call looks at
 * a numbered sheet of the crops and says what each tile shows - the Rahayu
 * run had sent a pair of feet as the father's face and the boy as the
 * grandmother. Pure, so the decision can be tested without a model.
 */
object FaceCheck {

    /** What the model said about tile [n] (1-based, in the order the crops were laid out). */
    data class Verdict(val n: Int, val face: Boolean, val id: String?)

    /**
     * Ids of [expected] (in tile order) whose crop the model confirmed: the
     * tile shows one face, and the person named is the one we cut - or the
     * model named nobody it knows. A tile the model attributes to a DIFFERENT
     * listed person is a neighbour's face, and is rejected.
     */
    fun verified(expected: List<String>, verdicts: List<Verdict>): Set<String> {
        val byTile = verdicts.associateBy { it.n }
        return expected.mapIndexedNotNull { i, id ->
            val verdict = byTile[i + 1] ?: return@mapIndexedNotNull null
            val named = verdict.id?.trim()?.takeIf { it.isNotBlank() }
            val someoneElse = named != null && named != id && named in expected
            if (verdict.face && !someoneElse) id else null
        }.toSet()
    }

    /** Fewer than half the crops passed: the boxes as a whole are wrong, not one stray face. */
    fun mostlyWrong(expected: List<String>, verified: Set<String>): Boolean =
        expected.isNotEmpty() && verified.size * 2 < expected.size
}
