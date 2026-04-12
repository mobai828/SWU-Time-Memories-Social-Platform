-- Create a public bucket for user avatars and site background images
insert into storage.buckets (id, name, public)
values ('public-assets', 'public-assets', true)
on conflict (id) do nothing;

-- Set up RLS for the public-assets bucket
-- 1. Allow public access to read files
create policy "Public Access"
  on storage.objects for select
  using ( bucket_id = 'public-assets' );

-- 2. Allow authenticated users to upload files
create policy "Authenticated users can upload files"
  on storage.objects for insert
  to authenticated
  with check ( bucket_id = 'public-assets' );

-- 3. Allow authenticated users to update their own files (or any file since this is a simple setup)
create policy "Users can update files"
  on storage.objects for update
  to authenticated
  using ( bucket_id = 'public-assets' );

-- 4. Allow authenticated users to delete files
create policy "Users can delete files"
  on storage.objects for delete
  to authenticated
  using ( bucket_id = 'public-assets' );
