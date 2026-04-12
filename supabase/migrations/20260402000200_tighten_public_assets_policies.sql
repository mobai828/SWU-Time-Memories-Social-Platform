drop policy if exists "Authenticated users can upload files" on storage.objects;
drop policy if exists "Users can update files" on storage.objects;
drop policy if exists "Users can delete files" on storage.objects;

create policy "Users can upload their own avatars"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'public-assets'
    and split_part(name, '/', 1) = 'avatars'
    and split_part(name, '/', 2) = auth.uid()::text
  );

create policy "Admins can upload login backgrounds"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'public-assets'
    and split_part(name, '/', 1) = 'login-backgrounds'
    and exists (
      select 1
      from users
      where id = auth.uid() and role = 'admin'
    )
  );

create policy "Users can update their own avatars"
  on storage.objects for update
  to authenticated
  using (
    bucket_id = 'public-assets'
    and split_part(name, '/', 1) = 'avatars'
    and split_part(name, '/', 2) = auth.uid()::text
  )
  with check (
    bucket_id = 'public-assets'
    and split_part(name, '/', 1) = 'avatars'
    and split_part(name, '/', 2) = auth.uid()::text
  );

create policy "Admins can update login backgrounds"
  on storage.objects for update
  to authenticated
  using (
    bucket_id = 'public-assets'
    and split_part(name, '/', 1) = 'login-backgrounds'
    and exists (
      select 1
      from users
      where id = auth.uid() and role = 'admin'
    )
  )
  with check (
    bucket_id = 'public-assets'
    and split_part(name, '/', 1) = 'login-backgrounds'
    and exists (
      select 1
      from users
      where id = auth.uid() and role = 'admin'
    )
  );

create policy "Users can delete their own avatars"
  on storage.objects for delete
  to authenticated
  using (
    bucket_id = 'public-assets'
    and split_part(name, '/', 1) = 'avatars'
    and split_part(name, '/', 2) = auth.uid()::text
  );

create policy "Admins can delete login backgrounds"
  on storage.objects for delete
  to authenticated
  using (
    bucket_id = 'public-assets'
    and split_part(name, '/', 1) = 'login-backgrounds'
    and exists (
      select 1
      from users
      where id = auth.uid() and role = 'admin'
    )
  );
