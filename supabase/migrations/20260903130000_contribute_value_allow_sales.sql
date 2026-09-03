-- Arch Decision 17: let a SALE price also seed the crowdsourced current value. The existing
-- "owners may contribute" INSERT gate required a matching non-deleted collection_copies row, so a
-- sale of an item the user no longer owns (the normal case) couldn't contribute. A realized sale is
-- a strong current-value signal ("if you sold it, you owned it"), so accept a matching non-deleted
-- sales row as ownership proof too. The one-point-per-user upsert (contribute_value RPC) is unchanged.
drop policy "owners may contribute" on public.set_value_contributions;
create policy "owners may contribute" on public.set_value_contributions
    for insert with check (
        auth.uid() = user_id
        and (
            exists (
                select 1 from public.collection_copies c
                where c.user_id = auth.uid()
                  and c.deleted = false
                  and (
                      (set_value_contributions.set_id is not null and c.set_id = set_value_contributions.set_id)
                      or (set_value_contributions.fig_num is not null and c.fig_num = set_value_contributions.fig_num)
                  )
            )
            or exists (
                select 1 from public.sales s
                where s.user_id = auth.uid()
                  and s.deleted = false
                  and (
                      (set_value_contributions.set_id is not null and s.set_id = set_value_contributions.set_id)
                      or (set_value_contributions.fig_num is not null and s.fig_num = set_value_contributions.fig_num)
                  )
            )
        )
    );
