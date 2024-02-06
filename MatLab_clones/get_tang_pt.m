function z = get_tang_pt(z1,z2,r1,r2,hes)
%z=get_tang_pt(z1,z2,r1,r2,hes) depends on geometry
%   
z=0.0; % return point as complex

if hes>0
	z=sph_tangency(z1,z2,r1,r2);
elseif hes<0
	z=hyp_tangency(z1,z2,r1,r2);
else
	z=eucl_tangency(z1,z2,r1,r2);
end

